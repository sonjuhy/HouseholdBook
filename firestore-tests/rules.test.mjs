// firestore.rules 보안 규칙 테스트
// 실행: cd firestore-tests && npm install && npm test   (Java 11+ 필요, Firebase 에뮬레이터가 자동으로 켜졌다 꺼짐)
// 앱(RemoteSyncManager.kt)이 쓰는 경로·필드와 같은 모양으로 검증합니다.
import { after, afterEach, before, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, arrayUnion } from 'firebase/firestore';

const ROOM = 'HB-ABCD-EFGH';
const OWNER = { uid: 'owner-uid', email: 'owner@gmail.com' };
const PARTNER = { uid: 'partner-uid', email: 'partner@gmail.com' };
const STRANGER = { uid: 'stranger-uid', email: 'stranger@gmail.com' };

let env;

/** 구글 로그인(이메일 인증 완료) 사용자 */
function googleUser({ uid, email }) {
  return env
    .authenticatedContext(uid, {
      email,
      email_verified: true,
      firebase: { sign_in_provider: 'google.com' },
    })
    .firestore();
}

function anonymousUser() {
  return env
    .authenticatedContext('anon-uid', { firebase: { sign_in_provider: 'anonymous' } })
    .firestore();
}

function roomData(memberEmails = [OWNER.email]) {
  return {
    inviteCode: ROOM,
    ownerUid: OWNER.uid,
    ownerEmail: OWNER.email,
    memberEmails,
    memberNamesCsv: '나',
    updatedAt: 1,
  };
}

function transaction(remoteId, overrides = {}) {
  return {
    remoteId,
    spaceId: 'space_shared_default',
    type: 'EXPENSE',
    amount: 18900,
    merchantOrTitle: '쿠팡',
    isCanceled: false,
    transactionDate: 1,
    updatedAt: 1,
    ...overrides,
  };
}

/** 규칙을 끈 상태로 기존 방(과 거래)을 만들어 둠 */
async function seedRoom(memberEmails = [OWNER.email]) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'shared_spaces', ROOM), roomData(memberEmails));
    await setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-1'), transaction('tx-1'));
  });
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-householdbook',
    // RULES_FILE로 다른 규칙 파일을 지정할 수 있음 (규칙을 일부러 느슨하게 바꿔 테스트가 실패하는지 확인할 때)
    firestore: {
      rules: readFileSync(process.env.RULES_FILE ?? new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

afterEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  await env.cleanup();
});

describe('로그인 방식', () => {
  test('익명 로그인은 방을 만들거나 읽을 수 없다', async () => {
    await seedRoom();
    const db = anonymousUser();
    await assertFails(getDoc(doc(db, 'shared_spaces', ROOM)));
    await assertFails(setDoc(doc(db, 'shared_spaces', 'HB-WXYZ-2345'), { ...roomData(), inviteCode: 'HB-WXYZ-2345' }));
  });

  test('로그인하지 않으면 아무것도 읽을 수 없다', async () => {
    await seedRoom();
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-1')));
  });
});

describe('방 만들기', () => {
  test('구글 사용자는 올바른 형식의 초대번호로 본인만 멤버인 방을 만들 수 있다', async () => {
    const db = googleUser(OWNER);
    await assertSucceeds(getDoc(doc(db, 'shared_spaces', ROOM))); // 없는 방 확인은 허용
    await assertSucceeds(setDoc(doc(db, 'shared_spaces', ROOM), roomData()));
  });

  test('예전 고정 초대번호(HB-2026)로는 방을 만들 수 없다', async () => {
    const db = googleUser(OWNER);
    await assertFails(setDoc(doc(db, 'shared_spaces', 'HB-2026'), { ...roomData(), inviteCode: 'HB-2026' }));
  });

  test('처음부터 다른 사람을 멤버로 넣어 방을 만들 수 없다', async () => {
    const db = googleUser(OWNER);
    await assertFails(setDoc(doc(db, 'shared_spaces', ROOM), roomData([OWNER.email, STRANGER.email])));
  });

  test('남의 이름(방장)으로 방을 만들 수 없다', async () => {
    const db = googleUser(STRANGER);
    await assertFails(setDoc(doc(db, 'shared_spaces', ROOM), roomData([STRANGER.email])));
  });
});

describe('멤버 전용 접근', () => {
  test('초대번호를 알아도 멤버가 아니면 방과 거래를 읽을 수 없다', async () => {
    await seedRoom();
    const db = googleUser(STRANGER);
    await assertFails(getDoc(doc(db, 'shared_spaces', ROOM)));
    await assertFails(getDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-1')));
    await assertFails(setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-2'), transaction('tx-2')));
  });

  test('멤버가 아닌 사람은 자기 자신을 멤버로 추가할 수 없다', async () => {
    await seedRoom();
    const db = googleUser(STRANGER);
    await assertFails(updateDoc(doc(db, 'shared_spaces', ROOM), { memberEmails: arrayUnion(STRANGER.email) }));
  });

  test('방장이 상대를 멤버로 추가하면 상대도 거래를 읽고 쓸 수 있다', async () => {
    await seedRoom();
    const owner = googleUser(OWNER);
    await assertSucceeds(updateDoc(doc(owner, 'shared_spaces', ROOM), { memberEmails: arrayUnion(PARTNER.email) }));

    const partner = googleUser(PARTNER);
    await assertSucceeds(getDoc(doc(partner, 'shared_spaces', ROOM, 'transactions', 'tx-1')));
    await assertSucceeds(setDoc(doc(partner, 'shared_spaces', ROOM, 'transactions', 'tx-2'), transaction('tx-2')));
    await assertSucceeds(deleteDoc(doc(partner, 'shared_spaces', ROOM, 'transactions', 'tx-2')));
  });

  test('멤버라도 방장을 내보내거나 방장 정보를 바꿀 수 없다', async () => {
    await seedRoom([OWNER.email, PARTNER.email]);
    const partner = googleUser(PARTNER);
    await assertFails(updateDoc(doc(partner, 'shared_spaces', ROOM), { memberEmails: [PARTNER.email] }));
    await assertFails(updateDoc(doc(partner, 'shared_spaces', ROOM), { ownerUid: PARTNER.uid, ownerEmail: PARTNER.email }));
  });
});

describe('공유 거래 내용 검증', () => {
  test('개인 용돈 공간 거래는 공유 장부에 올릴 수 없다', async () => {
    await seedRoom();
    const db = googleUser(OWNER);
    await assertFails(
      setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-p'), transaction('tx-p', { spaceId: 'space_personal_default' }))
    );
  });

  test('문서 ID와 remoteId가 다르거나 금액이 정수가 아니면 거부된다', async () => {
    await seedRoom();
    const db = googleUser(OWNER);
    await assertFails(setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-a'), transaction('tx-b')));
    await assertFails(setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-c'), transaction('tx-c', { amount: '18900' })));
  });
});

describe('초대장', () => {
  const invitationId = `${ROOM}__${PARTNER.email}`;
  const invitation = {
    inviteCode: ROOM,
    inviterUid: OWNER.uid,
    inviterEmail: OWNER.email,
    inviterName: '나',
    partnerEmail: PARTNER.email,
    partnerName: '짝꿍',
    memberNamesCsv: '나, 짝꿍',
    status: 'PENDING',
    createdAt: 1,
  };

  test('상대가 방 멤버로 추가된 뒤에만 초대장을 만들 수 있다', async () => {
    await seedRoom();
    const owner = googleUser(OWNER);
    await assertFails(setDoc(doc(owner, 'invitations', invitationId), invitation));

    await updateDoc(doc(owner, 'shared_spaces', ROOM), { memberEmails: arrayUnion(PARTNER.email) });
    await assertSucceeds(setDoc(doc(owner, 'invitations', invitationId), invitation));
  });

  test('초대받은 사람은 수락만 할 수 있고 다른 방으로 바꿀 수 없다', async () => {
    await seedRoom([OWNER.email, PARTNER.email]);
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'invitations', invitationId), invitation);
    });
    const partner = googleUser(PARTNER);
    await assertSucceeds(getDoc(doc(partner, 'invitations', invitationId)));
    await assertFails(updateDoc(doc(partner, 'invitations', invitationId), { inviteCode: 'HB-WXYZ-2345' }));
    await assertSucceeds(
      updateDoc(doc(partner, 'invitations', invitationId), {
        status: 'ACCEPTED',
        acceptedAt: 2,
        partnerUid: PARTNER.uid,
        partnerName: '짝꿍',
      })
    );
  });

  test('관계없는 사람은 초대장을 읽을 수 없다', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'invitations', invitationId), invitation);
    });
    await assertFails(getDoc(doc(googleUser(STRANGER), 'invitations', invitationId)));
  });
});

describe('사용자 프로필', () => {
  test('본인 프로필만 읽고 쓸 수 있다', async () => {
    const owner = googleUser(OWNER);
    const profile = { uid: OWNER.uid, email: OWNER.email, userTier: 'FREE' };
    await assertSucceeds(setDoc(doc(owner, 'users', OWNER.uid), profile));
    await assertSucceeds(getDoc(doc(owner, 'users', OWNER.uid)));

    const stranger = googleUser(STRANGER);
    await assertFails(getDoc(doc(stranger, 'users', OWNER.uid)));
    await assertFails(setDoc(doc(stranger, 'users', OWNER.uid), profile));
  });
});
