package com.budget.householdbook.data.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.dao.LedgerSpaceDao
import com.budget.householdbook.data.local.dao.TransactionDao
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.data.work.BackgroundWorkScheduler
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.model.UserTier
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/**
 * 구글 로그인 결과 및 서버에서 판별된 플랜(Free/Pro)·초대 정보
 */
data class GoogleLoginResult(
    val uid: String,
    val email: String,
    val displayName: String,
    val resolvedTier: UserTier,
    val inviteCode: String,
    val memberNamesCsv: String,
    val isInvitedJoin: Boolean,
    val isExistingUser: Boolean,
    val inviterName: String? = null
)

/**
 * 공유 장부 방의 멤버(memberEmails)가 아닌 계정으로 접근하려 할 때 발생
 */
class NotRoomMemberException(inviteCode: String) : IllegalStateException(
    "초대받지 않은 공유 장부입니다 ($inviteCode). 초대받은 구글 계정으로 로그인하거나 상대방에게 초대를 요청해주세요."
)

/**
 * Firebase(Google Auth + Cloud Firestore) 실시간 동기화 및 구글 계정 초대 매니저
 *
 * Firestore 구조 (`firestore.rules`와 함께 변경해야 함):
 * - `users/{uid}`: 내 프로필·플랜 (본인만 접근)
 * - `invitations/{inviteCode}__{partnerEmail}`: 초대장 (초대한 멤버·초대받은 사람만 접근)
 * - `shared_spaces/{inviteCode}`: 방 정보 + `memberEmails` 멤버 명단 (멤버만 접근)
 * - `shared_spaces/{inviteCode}/transactions/{remoteId}`: 공통 생활비 내역 (멤버만 접근)
 *
 * 모든 클라우드 접근은 구글 공식 인증 창으로 로그인한 계정만 가능합니다 (익명 로그인 사용 안 함).
 */
class RemoteSyncManager(
    private val context: Context,
    private val transactionDao: TransactionDao,
    private val spaceDao: LedgerSpaceDao,
    private val workScheduler: BackgroundWorkScheduler
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lastSyncMessage = MutableStateFlow("휴대폰에 안전하게 자동 저장 중")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    private var activeRoomCode: String? = null
    private var snapshotRegistration: ListenerRegistration? = null

    // 이번 실행 중 멤버 확인을 마친 "이메일|초대번호" (매 동기화마다 방 문서를 다시 읽지 않기 위함)
    private var verifiedRoomKey: String? = null

    fun isFirebaseConfigured(): Boolean {
        return runCatching {
            FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * 구글 공식 인증으로 로그인된 Firebase 사용자 (익명·이메일 없는 세션은 제외)
     */
    /**
     * Credential Manager의 'Google 계정으로 로그인' 창을 띄워 Google ID 토큰을 받음
     * - Firebase 콘솔에 앱 서명 SHA-1이 등록되어 있고, 그 뒤 받은 google-services.json에
     *   웹 클라이언트 ID(default_web_client_id)가 들어 있어야 동작
     */
    private suspend fun requestGoogleIdToken(activity: Activity): String {
        val webClientId = defaultWebClientId()
            ?: throw IllegalStateException(GOOGLE_SIGN_IN_SETUP_MESSAGE)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val response = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw IllegalStateException("구글 로그인을 취소했습니다.", e)
        } catch (e: NoCredentialException) {
            throw IllegalStateException("이 기기에 로그인된 구글 계정이 없습니다. 기기 설정에서 구글 계정을 추가한 뒤 다시 시도해주세요.", e)
        } catch (e: GetCredentialException) {
            throw IllegalStateException(friendlySignInError(e), e)
        }
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("구글 계정 정보를 받지 못했습니다. 다시 시도해주세요.")
    }

    /** google-services 플러그인이 만든 웹 클라이언트 ID (google-services.json에 OAuth 클라이언트가 없으면 null) */
    private fun defaultWebClientId(): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId == 0) null else context.getString(resId).takeIf { it.isNotBlank() }
    }

    /** Firebase·Google 로그인 오류를 사용자가 할 일 위주의 문장으로 */
    private fun friendlySignInError(error: Throwable): String {
        val raw = "${error.message} ${error.cause?.message}"
        return when {
            listOf("INVALID_CERT_HASH", "28444", "Developer console is not set up", "10:").any { raw.contains(it) } ->
                GOOGLE_SIGN_IN_SETUP_MESSAGE
            raw.contains("network", ignoreCase = true) || raw.contains("7:") ->
                "인터넷 연결을 확인한 뒤 다시 시도해주세요."
            raw.contains("OPERATION_NOT_ALLOWED") || raw.contains("not enabled", ignoreCase = true) ->
                "Firebase 콘솔 > Authentication에서 Google 로그인을 사용 설정해주세요."
            else -> "구글 로그인 중 문제가 발생했습니다. (${error.message ?: error.javaClass.simpleName})"
        }
    }

    /** 지금 공유 장부에 업로드할 수 있는 상태인지 (Firebase 설정 + 구글 계정 로그인) */
    fun canSyncNow(): Boolean = isFirebaseConfigured() && currentGoogleUser() != null

    private fun currentGoogleUser(): FirebaseUser? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        if (user.isAnonymous || user.email.isNullOrBlank()) return null
        if (user.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) return null
        return user
    }

    private fun userProfileDoc(firestore: FirebaseFirestore, uid: String) =
        firestore.collection(USERS).document(uid)

    private fun invitationDoc(firestore: FirebaseFirestore, inviteCode: String, partnerEmail: String) =
        firestore.collection(INVITATIONS).document(invitationDocId(inviteCode, partnerEmail))

    private fun roomDoc(firestore: FirebaseFirestore, inviteCode: String) =
        firestore.collection(SHARED_SPACES).document(inviteCode)

    private fun roomTransactions(firestore: FirebaseFirestore, inviteCode: String) =
        roomDoc(firestore, inviteCode).collection("transactions")

    /**
     * 방 문서를 확인해 내가 멤버인지 검증합니다. 방이 아직 없으면 [allowCreate]일 때 내가 방장인 새 방을 만듭니다.
     * 멤버가 아니면 [NotRoomMemberException]을 던집니다.
     */
    private suspend fun ensureRoomMembership(
        firestore: FirebaseFirestore,
        inviteCode: String,
        uid: String,
        email: String,
        memberNamesCsv: String?,
        allowCreate: Boolean
    ) {
        val ref = roomDoc(firestore, inviteCode)
        val snap = try {
            ref.get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw NotRoomMemberException(inviteCode)
            }
            throw e
        }
        val now = System.currentTimeMillis()

        if (!snap.exists()) {
            if (!allowCreate) throw NotRoomMemberException(inviteCode)
            ref.set(
                mapOf(
                    "inviteCode" to inviteCode,
                    "ownerUid" to uid,
                    "ownerEmail" to email,
                    "memberEmails" to listOf(email),
                    "memberNamesCsv" to memberNamesCsv.orEmpty(),
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()
        } else {
            val members = (snap.get("memberEmails") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (email !in members) throw NotRoomMemberException(inviteCode)
            if (!memberNamesCsv.isNullOrBlank() && snap.getString("memberNamesCsv") != memberNamesCsv) {
                ref.set(
                    mapOf("memberNamesCsv" to memberNamesCsv, "updatedAt" to now),
                    SetOptions.merge()
                ).await()
            }
        }
        verifiedRoomKey = "$email|$inviteCode"
    }

    /**
     * 내 이메일로 도착한 대기 중(PENDING) 초대장 중 가장 최근 것
     */
    private suspend fun findPendingInvitation(firestore: FirebaseFirestore, email: String): DocumentSnapshot? {
        return firestore.collection(INVITATIONS)
            .whereEqualTo("partnerEmail", email)
            .get()
            .await()
            .documents
            .filter { it.getString("status") == "PENDING" }
            .maxByOrNull { it.getLong("createdAt") ?: 0L }
    }

    /**
     * 구글 계정으로 로그인하고, Firestore에서
     * 1) 도착한 초대장(`invitations`)이 있는지 확인하고,
     * 2) 기존 회원(`users`)의 플랜이 PRO인지 FREE인지 확인하여 그에 맞게 자동으로 설정합니다.
     *
     * 이메일을 직접 입력해도 반드시 구글 공식 인증 창으로 본인 확인을 거치며,
     * 인증된 계정이 입력한 이메일과 다르면 로그인하지 않습니다.
     */
    suspend fun signInWithGoogleAccount(
        activity: Activity?,
        manualGoogleEmail: String? = null,
        manualDisplayName: String? = null
    ): Result<GoogleLoginResult> {
        if (!isFirebaseConfigured()) {
            return Result.failure(IllegalStateException("Firebase 설정(google-services.json)을 확인해주세요."))
        }

        return runCatching {
            val auth = FirebaseAuth.getInstance()
            val requestedEmail = manualGoogleEmail?.trim()?.lowercase().orEmpty()

            // 1. 이미 같은 구글 계정으로 인증되어 있으면 재사용, 아니면 공식 Google OAuth 창으로 로그인
            var user = currentGoogleUser()
                ?.takeIf { requestedEmail.isBlank() || it.email?.lowercase() == requestedEmail }
            if (user == null) {
                if (activity == null) {
                    throw IllegalStateException("구글 인증 창을 열 수 없습니다. 앱 화면에서 다시 시도해주세요.")
                }
                // 예전 버전의 익명 세션이나 다른 구글 계정 세션은 정리
                if (auth.currentUser != null) auth.signOut()
                // Firebase가 Android에 권장하는 방식: Credential Manager로 Google ID 토큰을 받아 Firebase에 로그인
                // (이전에는 OAuthProvider("google.com") 웹 창을 띄웠으나 Firebase 문서의 Android용 Google 로그인 방식이 아님)
                val idToken = requestGoogleIdToken(activity)
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                user = try {
                    auth.signInWithCredential(credential).await().user
                } catch (e: Exception) {
                    throw IllegalStateException(friendlySignInError(e), e)
                }
            }

            val email = user?.email?.trim()?.lowercase().orEmpty()
            if (user == null || email.isBlank()) {
                auth.signOut()
                throw IllegalStateException("구글 계정 정보를 확인하지 못했습니다. 다시 로그인해주세요.")
            }
            if (requestedEmail.isNotBlank() && requestedEmail != email) {
                auth.signOut()
                throw IllegalArgumentException(
                    "입력한 이메일($requestedEmail)과 인증한 구글 계정($email)이 다릅니다. 같은 계정으로 다시 로그인해주세요."
                )
            }

            val uid = user.uid
            val displayName = manualDisplayName?.trim()?.takeIf { it.isNotBlank() }
                ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")

            val firestore = FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            val userDocRef = userProfileDoc(firestore, uid)

            // 2. 먼저 내 구글 이메일로 도착한 '함께 쓰기 초대장'이 있는지 확인
            val inviteSnap = findPendingInvitation(firestore, email)
            if (inviteSnap != null) {
                val invitedCode = inviteSnap.getString("inviteCode")
                    ?.takeIf { isValidInviteCode(it) }
                    ?: throw IllegalStateException("초대장에 초대번호가 없습니다. 초대를 다시 요청해주세요.")
                val inviterName = inviteSnap.getString("inviterName") ?: "파트너"
                val memberNamesCsv = inviteSnap.getString("memberNamesCsv")
                    ?.takeIf { it.isNotBlank() }
                    ?: "$inviterName, $displayName"

                // 초대한 사람이 이미 방 멤버(memberEmails)에 내 이메일을 추가해 두었으므로 멤버 확인만 수행
                ensureRoomMembership(
                    firestore = firestore,
                    inviteCode = invitedCode,
                    uid = uid,
                    email = email,
                    memberNamesCsv = memberNamesCsv,
                    allowCreate = false
                )

                // 초대를 받은 구글 계정은 즉시 프리미엄(함께 쓰기) 및 해당 초대방으로 자동 연결
                val resolvedTier = UserTier.PRO
                inviteSnap.reference.set(
                    mapOf(
                        "status" to "ACCEPTED",
                        "acceptedAt" to now,
                        "partnerUid" to uid,
                        "partnerName" to displayName
                    ),
                    SetOptions.merge()
                ).await()

                userDocRef.set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to displayName,
                        "userTier" to resolvedTier.name,
                        "inviteCode" to invitedCode,
                        "memberNamesCsv" to memberNamesCsv,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                ).await()

                spaceDao.updateShareConfig(
                    spaceId = AppDatabase.SPACE_ID_SHARED,
                    memberNamesCsv = memberNamesCsv,
                    inviteCode = invitedCode,
                    isShared = true
                )

                return@runCatching GoogleLoginResult(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    resolvedTier = resolvedTier,
                    inviteCode = invitedCode,
                    memberNamesCsv = memberNamesCsv,
                    isInvitedJoin = true,
                    isExistingUser = true,
                    inviterName = inviterName
                )
            }

            // 3. 기존에 가입된 구글 계정 프로필에서 PRO인지 FREE인지 확인
            val userSnap = userDocRef.get().await()

            if (userSnap.exists()) {
                val savedTierStr = userSnap.getString("userTier") ?: UserTier.FREE.name
                val resolvedTier = runCatching { UserTier.valueOf(savedTierStr) }
                    .getOrDefault(UserTier.FREE)
                val savedInviteCode = userSnap.getString("inviteCode")
                    ?.takeIf { isValidInviteCode(it) }
                    ?: generateInviteCode()
                val savedName = userSnap.getString("displayName")
                    ?.takeIf { it.isNotBlank() }
                    ?: displayName
                val savedMembers = userSnap.getString("memberNamesCsv")
                    ?.takeIf { it.isNotBlank() }
                    ?: if (resolvedTier == UserTier.PRO) "$savedName, 짝꿍" else "$savedName (혼자 쓰기)"

                userDocRef.set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to savedName,
                        "inviteCode" to savedInviteCode,
                        "lastLoginAt" to now
                    ),
                    SetOptions.merge()
                ).await()

                spaceDao.updateShareConfig(
                    spaceId = AppDatabase.SPACE_ID_SHARED,
                    memberNamesCsv = savedMembers,
                    inviteCode = if (resolvedTier == UserTier.PRO) savedInviteCode else null,
                    isShared = resolvedTier == UserTier.PRO
                )

                return@runCatching GoogleLoginResult(
                    uid = uid,
                    email = email,
                    displayName = savedName,
                    resolvedTier = resolvedTier,
                    inviteCode = savedInviteCode,
                    memberNamesCsv = savedMembers,
                    isInvitedJoin = false,
                    isExistingUser = true
                )
            }

            // 4. 처음 로그인한 신규 구글 계정인 경우: 기본(FREE)으로 시작하고 프로필을 Firestore에 생성
            val newTier = UserTier.FREE
            val generatedCode = generateInviteCode()
            val defaultMembers = "$displayName (혼자 쓰기)"

            userDocRef.set(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "userTier" to newTier.name,
                    "inviteCode" to generatedCode,
                    "memberNamesCsv" to defaultMembers,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()

            spaceDao.updateShareConfig(
                spaceId = AppDatabase.SPACE_ID_SHARED,
                memberNamesCsv = defaultMembers,
                inviteCode = null,
                isShared = false
            )

            GoogleLoginResult(
                uid = uid,
                email = email,
                displayName = displayName,
                resolvedTier = newTier,
                inviteCode = generatedCode,
                memberNamesCsv = defaultMembers,
                isInvitedJoin = false,
                isExistingUser = false
            )
        }
    }

    /**
     * 사용자가 플랜(Free/Pro)이나 공유 멤버를 변경했을 때 Firestore 프로필에도 저장합니다.
     * 구글 계정으로 로그인되어 있지 않으면 아무것도 하지 않습니다.
     */
    suspend fun syncUserProfileTierToCloud(
        email: String,
        displayName: String,
        tier: UserTier,
        inviteCode: String?,
        memberNamesCsv: String
    ) {
        if (!isFirebaseConfigured() || email.isBlank()) return
        runCatching {
            val user = currentGoogleUser() ?: return@runCatching
            val userEmail = user.email.orEmpty().trim().lowercase()
            if (userEmail != email.trim().lowercase()) return@runCatching
            val firestore = FirebaseFirestore.getInstance()
            val payload = buildMap<String, Any> {
                put("uid", user.uid)
                put("email", userEmail)
                put("displayName", displayName)
                put("userTier", tier.name)
                put("memberNamesCsv", memberNamesCsv)
                put("updatedAt", System.currentTimeMillis())
                inviteCode?.takeIf { isValidInviteCode(it) }?.let { put("inviteCode", it) }
            }
            userProfileDoc(firestore, user.uid).set(payload, SetOptions.merge()).await()
        }
    }

    /**
     * 상대방의 구글 계정(이메일)으로 함께 쓰기 초대장을 발송합니다.
     * 상대방 이메일을 방 멤버(memberEmails)에 추가하므로, 상대방이 해당 구글 계정으로 로그인하면
     * 초대번호 입력 없이도 자동으로 프리미엄 공유 장부에 연결됩니다.
     */
    suspend fun sendGoogleAccountInvitation(
        inviterEmail: String,
        inviterName: String,
        partnerGoogleEmail: String,
        partnerName: String,
        inviteCode: String
    ): Result<String> {
        if (!isFirebaseConfigured()) {
            return Result.failure(IllegalStateException("Firebase 연결을 확인해주세요."))
        }
        val user = currentGoogleUser()
            ?: return Result.failure(IllegalStateException("함께 쓰기 초대는 구글 계정으로 로그인한 뒤 보낼 수 있습니다."))
        val myEmail = user.email.orEmpty().trim().lowercase()
        val cleanPartnerEmail = partnerGoogleEmail.trim().lowercase()
        if (!cleanPartnerEmail.contains("@") || cleanPartnerEmail.contains("/")) {
            return Result.failure(IllegalArgumentException("초대할 분의 올바른 구글 이메일(예: partner@gmail.com)을 입력해주세요."))
        }
        if (cleanPartnerEmail == myEmail) {
            return Result.failure(IllegalArgumentException("내 구글 계정이 아닌 상대방의 이메일을 입력해주세요."))
        }
        if (inviterEmail.isNotBlank() && inviterEmail.trim().lowercase() != myEmail) {
            return Result.failure(IllegalStateException("로그인 정보가 바뀌었습니다. 구글 계정으로 다시 로그인해주세요."))
        }

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()
            val cleanCode = inviteCode.trim().uppercase().takeIf { isValidInviteCode(it) }
                ?: generateInviteCode()
            val resolvedPartnerName = partnerName.trim().ifBlank { cleanPartnerEmail.substringBefore("@") }
            val resolvedInviterName = inviterName.trim().ifBlank { "나" }
            val combinedMembers = "$resolvedInviterName, $resolvedPartnerName"

            // 1. 내가 이 방의 멤버인지 확인 (방이 없으면 내가 방장인 새 방 생성)
            ensureRoomMembership(
                firestore = firestore,
                inviteCode = cleanCode,
                uid = user.uid,
                email = myEmail,
                memberNamesCsv = null,
                allowCreate = true
            )

            // 2. 상대방 이메일을 방 멤버로 추가 (규칙상 초대장보다 먼저 추가되어 있어야 함)
            roomDoc(firestore, cleanCode).set(
                mapOf(
                    "memberNamesCsv" to combinedMembers,
                    "memberEmails" to FieldValue.arrayUnion(cleanPartnerEmail),
                    "updatedAt" to now
                ),
                SetOptions.merge()
            ).await()

            // 3. 상대방 이메일 주소로 초대장 문서 생성
            invitationDoc(firestore, cleanCode, cleanPartnerEmail).set(
                mapOf(
                    "inviteCode" to cleanCode,
                    "inviterUid" to user.uid,
                    "inviterEmail" to myEmail,
                    "inviterName" to resolvedInviterName,
                    "partnerEmail" to cleanPartnerEmail,
                    "partnerName" to resolvedPartnerName,
                    "memberNamesCsv" to combinedMembers,
                    "status" to "PENDING",
                    "createdAt" to now
                ),
                SetOptions.merge()
            ).await()

            // 4. 내 프로필에도 멤버 및 초대번호 저장
            userProfileDoc(firestore, user.uid).set(
                mapOf(
                    "uid" to user.uid,
                    "email" to myEmail,
                    "userTier" to UserTier.PRO.name,
                    "inviteCode" to cleanCode,
                    "memberNamesCsv" to combinedMembers,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            ).await()

            spaceDao.updateShareConfig(
                spaceId = AppDatabase.SPACE_ID_SHARED,
                memberNamesCsv = combinedMembers,
                inviteCode = cleanCode,
                isShared = true
            )

            cleanCode
        }
    }

    suspend fun signOutGoogleAccount() {
        stopRealtimeListener()
        verifiedRoomKey = null
        runCatching {
            if (isFirebaseConfigured()) {
                FirebaseAuth.getInstance().signOut()
            }
        }
        _lastSyncMessage.value = "내 휴대폰에만 보관 중 (로그아웃됨)"
    }

    suspend fun syncIfEligible(userTier: UserTier, isRemoteShared: Boolean): Boolean {
        if (userTier != UserTier.PRO) {
            stopRealtimeListener()
            _lastSyncMessage.value = "내 휴대폰에만 보관 중"
            return false
        }
        if (!isRemoteShared) {
            stopRealtimeListener()
            _lastSyncMessage.value = "저장 완료 (혼자 보는 장부)"
            return false
        }

        if (!isFirebaseConfigured()) {
            transactionDao.markAllUploadsSynced()
            transactionDao.purgePendingDeletes()
            _lastSyncMessage.value = "휴대폰에 저장됨 (Firebase 설정 파일 추가 시 실시간 공유 활성화)"
            return false
        }

        // 공유 장부는 구글 계정으로 인증된 멤버만 접근 가능 (대기 내역은 로그인 후 업로드)
        val user = currentGoogleUser()
        if (user == null) {
            stopRealtimeListener()
            _lastSyncMessage.value = "휴대폰에 저장됨 (구글 계정으로 로그인하면 함께 쓰는 장부에 반영)"
            return false
        }
        val email = user.email.orEmpty().trim().lowercase()

        return runCatching {
            val sharedSpace = spaceDao.getSpaceById(AppDatabase.SPACE_ID_SHARED)
            // 초대번호가 없거나 예전 형식(고정값 HB-2026 등)이면 추측 불가능한 새 번호를 발급해 저장
            val inviteCode = sharedSpace?.inviteCode?.takeIf { isValidInviteCode(it) }
                ?: generateInviteCode().also { newCode ->
                    spaceDao.updateShareConfig(
                        spaceId = AppDatabase.SPACE_ID_SHARED,
                        memberNamesCsv = sharedSpace?.memberNamesCsv ?: "나",
                        inviteCode = newCode,
                        isShared = true
                    )
                }

            val firestore = FirebaseFirestore.getInstance()
            if (verifiedRoomKey != "$email|$inviteCode") {
                ensureRoomMembership(
                    firestore = firestore,
                    inviteCode = inviteCode,
                    uid = user.uid,
                    email = email,
                    memberNamesCsv = null,
                    allowCreate = true
                )
            }

            ensureRealtimeListener(inviteCode)

            val collectionRef = roomTransactions(firestore, inviteCode)

            // 삭제 대기 내역을 먼저 원격에서 지운 뒤 로컬에서도 완전히 제거
            for (tx in transactionDao.getPendingDeleteTransactions(AppDatabase.SPACE_ID_SHARED)) {
                collectionRef.document(tx.remoteId).delete().await()
                transactionDao.deleteById(tx.id)
            }

            // 공통 생활비 공간의 내역만 업로드 (개인 용돈은 공유 장부로 절대 올리지 않음)
            val pendingList = transactionDao.getPendingUploadTransactions(AppDatabase.SPACE_ID_SHARED)
                .ifEmpty { transactionDao.getAllBySpaceOnce(AppDatabase.SPACE_ID_SHARED) }

            if (pendingList.isNotEmpty()) {
                val uploadedIds = mutableListOf<Long>()
                for (tx in pendingList) {
                    val payload = mapOf(
                        "remoteId" to tx.remoteId,
                        "spaceId" to tx.spaceId,
                        "type" to tx.type.name,
                        "amount" to tx.amount,
                        "categoryId" to tx.categoryId,
                        "subCategoryId" to tx.subCategoryId,
                        "merchantOrTitle" to tx.merchantOrTitle,
                        "memo" to tx.memo,
                        "isCanceled" to tx.isCanceled,
                        "authorName" to tx.authorName,
                        "transactionDate" to tx.transactionDate,
                        "updatedAt" to tx.updatedAt
                    )
                    collectionRef.document(tx.remoteId).set(payload, SetOptions.merge()).await()
                    uploadedIds.add(tx.id)
                }
                transactionDao.markSyncedByIds(uploadedIds)
            }

            _lastSyncMessage.value = "함께 쓰는 장부 실시간 반영 완료 (초대번호: $inviteCode)"
            true
        }.getOrElse { error ->
            if (error is NotRoomMemberException || error.isPermissionDenied()) {
                stopRealtimeListener()
                verifiedRoomKey = null
                _lastSyncMessage.value = error.message?.takeIf { error is NotRoomMemberException }
                    ?: "공유 장부에 접근할 권한이 없습니다. 초대받은 구글 계정으로 로그인해주세요."
            } else {
                _lastSyncMessage.value = "휴대폰에 먼저 저장됨 (인터넷 연결 시 자동 반영)"
                // 오프라인 등 일시적 실패: 인터넷이 연결되면 SyncWorker가 대신 업로드
                workScheduler.requestSharedSync()
            }
            false
        }
    }

    private fun Throwable.isPermissionDenied(): Boolean =
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private fun ensureRealtimeListener(inviteCode: String) {
        if (!isFirebaseConfigured()) return
        if (activeRoomCode == inviteCode && snapshotRegistration != null) return

        stopRealtimeListener()
        activeRoomCode = inviteCode

        runCatching {
            val firestore = FirebaseFirestore.getInstance()
            snapshotRegistration = roomTransactions(firestore, inviteCode)
                .addSnapshotListener { snapshot, error ->
                    if (error?.isPermissionDenied() == true) {
                        // 방에서 내보내졌거나 로그아웃된 경우: 더 이상 수신하지 않음
                        stopRealtimeListener()
                        verifiedRoomKey = null
                        _lastSyncMessage.value = "공유 장부에 접근할 권한이 없습니다. 초대받은 구글 계정으로 로그인해주세요."
                        return@addSnapshotListener
                    }
                    if (error != null || snapshot == null) return@addSnapshotListener
                    syncScope.launch {
                        // 다른 멤버가 삭제한 내역은 내 휴대폰에서도 제거
                        for (change in snapshot.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                val removedId = change.document.getString("remoteId") ?: change.document.id
                                transactionDao.deleteByRemoteId(removedId)
                            }
                        }
                        for (doc in snapshot.documents) {
                            val remoteId = doc.getString("remoteId") ?: doc.id
                            // 공유 장부에는 공통 생활비만 존재해야 하므로 다른 공간 문서는 무시
                            val spaceId = doc.getString("spaceId") ?: AppDatabase.SPACE_ID_SHARED
                            if (spaceId != AppDatabase.SPACE_ID_SHARED) continue
                            val typeName = doc.getString("type") ?: TransactionType.EXPENSE.name
                            val type = runCatching { TransactionType.valueOf(typeName) }
                                .getOrDefault(TransactionType.EXPENSE)
                            val amount = doc.getLong("amount") ?: continue
                            val categoryId = doc.getLong("categoryId") ?: 1L
                            val subCategoryId = doc.getLong("subCategoryId")
                            val merchantOrTitle = doc.getString("merchantOrTitle") ?: "공유 내역"
                            val memo = doc.getString("memo").orEmpty()
                            val isCanceled = doc.getBoolean("isCanceled") ?: false
                            val authorName = doc.getString("authorName") ?: "멤버"
                            val transactionDate = doc.getLong("transactionDate") ?: System.currentTimeMillis()
                            val updatedAt = doc.getLong("updatedAt") ?: transactionDate

                            val existing = transactionDao.getByRemoteId(remoteId)
                            if (existing?.syncStatus == SyncStatus.PENDING_DELETE) continue
                            if (existing == null) {
                                transactionDao.insertTransaction(
                                    TransactionEntity(
                                        remoteId = remoteId,
                                        spaceId = spaceId,
                                        type = type,
                                        amount = amount,
                                        categoryId = categoryId,
                                        subCategoryId = subCategoryId,
                                        merchantOrTitle = merchantOrTitle,
                                        memo = memo,
                                        isCanceled = isCanceled,
                                        authorName = authorName,
                                        transactionDate = transactionDate,
                                        updatedAt = updatedAt,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                            } else if (updatedAt > existing.updatedAt) {
                                transactionDao.insertTransaction(
                                    existing.copy(
                                        type = type,
                                        amount = amount,
                                        categoryId = categoryId,
                                        subCategoryId = subCategoryId,
                                        merchantOrTitle = merchantOrTitle,
                                        memo = memo,
                                        isCanceled = isCanceled,
                                        authorName = authorName,
                                        transactionDate = transactionDate,
                                        updatedAt = updatedAt,
                                        syncStatus = SyncStatus.SYNCED
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }

    companion object {
        /** SHA-1 미등록 등 Firebase 설정 문제로 구글 로그인이 막혔을 때 안내 */
        const val GOOGLE_SIGN_IN_SETUP_MESSAGE =
            "구글 로그인 설정이 끝나지 않았습니다. Firebase 콘솔에 이 앱의 SHA-1 인증서 지문을 등록하고, " +
                "새 google-services.json을 받아 앱에 넣은 뒤 다시 빌드해주세요."

        private const val USERS = "users"
        private const val INVITATIONS = "invitations"
        private const val SHARED_SPACES = "shared_spaces"

        // 헷갈리는 문자(0/O, 1/I/L)를 뺀 31자 중 8자리 → 약 8,500억 가지 조합
        private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val secureRandom = SecureRandom()

        // firestore.rules의 isValidInviteCode()와 같은 형식으로 유지해야 함
        private val INVITE_CODE_REGEX = Regex("^HB-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$")

        fun generateInviteCode(): String {
            val chars = (1..8).map { INVITE_CODE_ALPHABET[secureRandom.nextInt(INVITE_CODE_ALPHABET.length)] }
            return "HB-" + chars.take(4).joinToString("") + "-" + chars.drop(4).joinToString("")
        }

        /** 서버 규칙에서 방을 만들 수 있는 초대번호 형식인지 (예전 고정값 `HB-2026` 등은 false) */
        fun isValidInviteCode(code: String?): Boolean = code != null && INVITE_CODE_REGEX.matches(code)

        /** 초대장 문서 ID — firestore.rules에서 `{inviteCode}__{partnerEmail}` 형식을 검증함 */
        fun invitationDocId(inviteCode: String, partnerEmail: String): String =
            "${inviteCode}__${partnerEmail.trim().lowercase()}"
    }

    private fun stopRealtimeListener() {
        snapshotRegistration?.remove()
        snapshotRegistration = null
        activeRoomCode = null
    }
}
