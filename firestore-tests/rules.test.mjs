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
import {
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  arrayUnion,
  arrayRemove,
  collection,
  query,
  where,
} from 'firebase/firestore';

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

/** 앱(RemoteSyncManager.toPayload)이 올리는 공용 분류와 같은 모양 */
function category(remoteId, overrides = {}) {
  return {
    remoteId,
    parentRemoteId: null,
    spaceId: 'space_shared_default',
    transactionType: 'EXPENSE',
    name: '식비',
    emoji: '🍔',
    colorHex: '#EF4444',
    sortOrder: 1,
    isArchived: false,
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

describe('공용 분류', () => {
  const categoryRef = (db, id) => doc(db, 'shared_spaces', ROOM, 'categories', id);

  test('방 멤버는 공용 분류를 읽고 올리고 숨길 수 있다', async () => {
    await seedRoom([OWNER.email, PARTNER.email]);
    const owner = googleUser(OWNER);
    await assertSucceeds(setDoc(categoryRef(owner, 'cat-1'), category('cat-1')));
    await assertSucceeds(setDoc(categoryRef(owner, 'cat-2'), category('cat-2', { parentRemoteId: 'cat-1', name: '배달/외식' })));

    const partner = googleUser(PARTNER);
    await assertSucceeds(getDocs(collection(partner, 'shared_spaces', ROOM, 'categories')));
    await assertSucceeds(setDoc(categoryRef(partner, 'cat-1'), category('cat-1', { isArchived: true, updatedAt: 2 })));
  });

  test('멤버가 아니면 공용 분류를 읽거나 쓸 수 없다', async () => {
    await seedRoom();
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(categoryRef(ctx.firestore(), 'cat-1'), category('cat-1'));
    });
    const stranger = googleUser(STRANGER);
    await assertFails(getDoc(categoryRef(stranger, 'cat-1')));
    await assertFails(setDoc(categoryRef(stranger, 'cat-2'), category('cat-2')));
    await assertFails(deleteDoc(categoryRef(stranger, 'cat-1')));
  });

  test('개인 분류, 문서 ID와 다른 remoteId, 빈 이름·너무 긴 이름, 잘못된 유형은 거부된다', async () => {
    await seedRoom();
    const db = googleUser(OWNER);
    await assertFails(setDoc(categoryRef(db, 'cat-p'), category('cat-p', { spaceId: 'space_personal_default' })));
    await assertFails(setDoc(categoryRef(db, 'cat-a'), category('cat-b')));
    await assertFails(setDoc(categoryRef(db, 'cat-e'), category('cat-e', { name: '' })));
    await assertFails(setDoc(categoryRef(db, 'cat-l'), category('cat-l', { name: '가'.repeat(51) })));
    await assertSucceeds(setDoc(categoryRef(db, 'cat-m'), category('cat-m', { name: '가'.repeat(50) })));
    await assertFails(setDoc(categoryRef(db, 'cat-t'), category('cat-t', { transactionType: 'GIFT' })));
    await assertFails(setDoc(categoryRef(db, 'cat-u'), category('cat-u', { updatedAt: '1' })));
  });

  test('혼자 남은 방장은 탈퇴하며 공용 분류도 지울 수 있다', async () => {
    await seedRoom();
    const owner = googleUser(OWNER);
    await assertSucceeds(setDoc(categoryRef(owner, 'cat-1'), category('cat-1')));
    const cats = await assertSucceeds(getDocs(collection(owner, 'shared_spaces', ROOM, 'categories')));
    for (const d of cats.docs) await assertSucceeds(deleteDoc(d.ref));
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

describe('멤버 관리 (초대 취소 · 내보내기 · 나가기)', () => {
  const INVITED = { uid: 'invited-uid', email: 'invited@gmail.com' };

  /** 방장 + 참여한 짝꿍 + 아직 참여 전인 초대 대상이 있는 방 */
  async function seedManagedRoom() {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'shared_spaces', ROOM), {
        ...roomData([OWNER.email, PARTNER.email, INVITED.email]),
        joinedEmails: [OWNER.email, PARTNER.email],
        memberNames: { [OWNER.email]: '나', [PARTNER.email]: '짝꿍', [INVITED.email]: '동생' },
      });
      await setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-1'), transaction('tx-1'));
      await setDoc(doc(db, 'invitations', `${ROOM}__${INVITED.email}`), {
        inviteCode: ROOM,
        inviterUid: OWNER.uid,
        inviterEmail: OWNER.email,
        inviterName: '나',
        partnerEmail: INVITED.email,
        partnerName: '동생',
        status: 'PENDING',
        createdAt: 1,
      });
    });
  }

  test('방장은 참여한 멤버를 내보낼 수 있고, 내보낸 멤버는 더 이상 거래를 읽지 못한다', async () => {
    await seedManagedRoom();
    await assertSucceeds(
      updateDoc(doc(googleUser(OWNER), 'shared_spaces', ROOM), {
        memberEmails: arrayRemove(PARTNER.email),
        joinedEmails: arrayRemove(PARTNER.email),
      })
    );
    await assertFails(getDoc(doc(googleUser(PARTNER), 'shared_spaces', ROOM, 'transactions', 'tx-1')));
  });

  test('방장이 아니면 참여한 다른 멤버를 내보낼 수 없다', async () => {
    await seedManagedRoom();
    await assertFails(
      updateDoc(doc(googleUser(PARTNER), 'shared_spaces', ROOM), {
        memberEmails: arrayRemove(OWNER.email),
      })
    );
    // 방장 말고 다른 참여 멤버를 지우는 것도 불가 (짝꿍이 초대 대상을 참여 처리한 뒤 지우는 우회 포함)
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'shared_spaces', ROOM), { joinedEmails: arrayUnion(INVITED.email) });
    });
    await assertFails(
      updateDoc(doc(googleUser(PARTNER), 'shared_spaces', ROOM), {
        memberEmails: arrayRemove(INVITED.email),
        joinedEmails: arrayRemove(INVITED.email),
      })
    );
  });

  test('멤버는 아직 참여하지 않은 초대를 취소할 수 있다', async () => {
    await seedManagedRoom();
    const partner = googleUser(PARTNER);
    await assertSucceeds(updateDoc(doc(partner, 'shared_spaces', ROOM), { memberEmails: arrayRemove(INVITED.email) }));
    // 초대장도 취소 상태로 (취소한 사람이 초대한 사람으로 기록됨)
    await assertSucceeds(
      updateDoc(doc(partner, 'invitations', `${ROOM}__${INVITED.email}`), {
        status: 'CANCELED',
        inviterUid: PARTNER.uid,
        inviterEmail: PARTNER.email,
      })
    );
  });

  test('멤버는 스스로 나갈 수 있지만 참여한 다른 멤버를 함께 빼며 나갈 수는 없다', async () => {
    await seedManagedRoom();
    // 초대 대상도 참여를 마친 상태로
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'shared_spaces', ROOM), { joinedEmails: arrayUnion(INVITED.email) });
    });
    const partner = googleUser(PARTNER);
    await assertFails(
      updateDoc(doc(partner, 'shared_spaces', ROOM), {
        memberEmails: [OWNER.email],
        joinedEmails: [OWNER.email],
      })
    );
    await assertSucceeds(
      updateDoc(doc(partner, 'shared_spaces', ROOM), {
        memberEmails: arrayRemove(PARTNER.email),
        joinedEmails: arrayRemove(PARTNER.email),
      })
    );
  });

  test('초대받은 사람은 자신만 참여 처리할 수 있다', async () => {
    await seedManagedRoom();
    const invited = googleUser(INVITED);
    await assertSucceeds(updateDoc(doc(invited, 'shared_spaces', ROOM), { joinedEmails: arrayUnion(INVITED.email) }));
    await assertSucceeds(getDoc(doc(invited, 'shared_spaces', ROOM, 'transactions', 'tx-1')));
  });

  test('명단에 없는 사람을 참여 처리하거나 참여 멤버를 명단에서만 지울 수 없다', async () => {
    await seedManagedRoom();
    await assertFails(
      updateDoc(doc(googleUser(OWNER), 'shared_spaces', ROOM), { joinedEmails: arrayUnion(STRANGER.email) })
    );
    await assertFails(
      updateDoc(doc(googleUser(OWNER), 'shared_spaces', ROOM), { memberEmails: arrayRemove(PARTNER.email) })
    );
    await assertFails(
      updateDoc(doc(googleUser(PARTNER), 'shared_spaces', ROOM), { joinedEmails: arrayUnion(INVITED.email) })
    );
  });
});

describe('방장 넘기기 · 회원 탈퇴', () => {
  const INVITED = { uid: 'invited-uid', email: 'invited@gmail.com' };
  const roomRef = (db) => doc(db, 'shared_spaces', ROOM);

  /** 방장 + 참여한 짝꿍 + 아직 참여 전인 초대 대상, 방장이 보낸 초대장 2장 */
  async function seedTransferRoom() {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(roomRef(db), {
        ...roomData([OWNER.email, PARTNER.email, INVITED.email]),
        joinedEmails: [OWNER.email, PARTNER.email],
      });
      await setDoc(doc(db, 'shared_spaces', ROOM, 'transactions', 'tx-1'), transaction('tx-1'));
      for (const target of [PARTNER, INVITED]) {
        await setDoc(doc(db, 'invitations', `${ROOM}__${target.email}`), {
          inviteCode: ROOM,
          inviterUid: OWNER.uid,
          inviterEmail: OWNER.email,
          partnerEmail: target.email,
          status: target === PARTNER ? 'ACCEPTED' : 'PENDING',
          createdAt: 1,
        });
      }
      await setDoc(doc(db, 'users', OWNER.uid), { uid: OWNER.uid, email: OWNER.email, userTier: 'PRO' });
    });
  }

  test('방장은 참여한 멤버에게 방장을 넘기고, 넘긴 뒤에는 나갈 수 있다', async () => {
    await seedTransferRoom();
    // 새 방장의 uid를 모르면 빈 값으로 넘기고, 새 방장이 자기 uid를 채움
    await assertSucceeds(
      updateDoc(roomRef(googleUser(OWNER)), { ownerEmail: PARTNER.email, ownerUid: '', updatedAt: 2 })
    );
    await assertSucceeds(updateDoc(roomRef(googleUser(PARTNER)), { ownerUid: PARTNER.uid }));
    await assertSucceeds(
      updateDoc(roomRef(googleUser(OWNER)), {
        memberEmails: arrayRemove(OWNER.email),
        joinedEmails: arrayRemove(OWNER.email),
      })
    );
    // 새 방장은 방장 권한으로 초대를 정리할 수 있음
    await assertSucceeds(updateDoc(roomRef(googleUser(PARTNER)), { memberEmails: arrayRemove(INVITED.email) }));
  });

  test('방장은 참여 전 초대 대상이나 명단 밖 사람에게 넘길 수 없고, 넘기면서 명단을 바꿀 수 없다', async () => {
    await seedTransferRoom();
    const owner = googleUser(OWNER);
    await assertFails(updateDoc(roomRef(owner), { ownerEmail: INVITED.email, ownerUid: INVITED.uid }));
    await assertFails(updateDoc(roomRef(owner), { ownerEmail: STRANGER.email, ownerUid: STRANGER.uid }));
    await assertFails(
      updateDoc(roomRef(owner), {
        ownerEmail: PARTNER.email,
        ownerUid: PARTNER.uid,
        memberEmails: arrayRemove(OWNER.email),
        joinedEmails: arrayRemove(OWNER.email),
      })
    );
  });

  test('방장이 아니면 방장을 가져오거나 방장 uid를 바꾸거나 방을 지울 수 없다', async () => {
    await seedTransferRoom();
    const partner = googleUser(PARTNER);
    await assertFails(updateDoc(roomRef(partner), { ownerEmail: PARTNER.email, ownerUid: PARTNER.uid }));
    await assertFails(updateDoc(roomRef(partner), { ownerUid: PARTNER.uid }));
    await assertFails(deleteDoc(roomRef(partner)));
  });

  test('방장을 넘긴 사람은 더 이상 방장 권한이 없다', async () => {
    await seedTransferRoom();
    await assertSucceeds(updateDoc(roomRef(googleUser(OWNER)), { ownerEmail: PARTNER.email, ownerUid: PARTNER.uid }));
    const formerOwner = googleUser(OWNER);
    await assertFails(
      updateDoc(roomRef(formerOwner), {
        memberEmails: arrayRemove(PARTNER.email),
        joinedEmails: arrayRemove(PARTNER.email),
      })
    );
    await assertFails(updateDoc(roomRef(formerOwner), { ownerEmail: OWNER.email, ownerUid: OWNER.uid }));
    await assertFails(deleteDoc(roomRef(formerOwner)));
  });

  test('혼자 남은 방장은 탈퇴하며 공유 거래·방·보낸 초대장·내 프로필을 지울 수 있다', async () => {
    await seedTransferRoom();
    // 짝꿍이 먼저 나간 상태
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(roomRef(ctx.firestore()), {
        memberEmails: [OWNER.email, INVITED.email],
        joinedEmails: [OWNER.email],
      });
    });
    const owner = googleUser(OWNER);
    const txs = await assertSucceeds(getDocs(collection(owner, 'shared_spaces', ROOM, 'transactions')));
    for (const d of txs.docs) await assertSucceeds(deleteDoc(d.ref));
    await assertSucceeds(deleteDoc(roomRef(owner)));

    const sent = await assertSucceeds(getDocs(query(collection(owner, 'invitations'), where('inviterUid', '==', OWNER.uid))));
    assertEqualCount(sent.size, 2);
    for (const d of sent.docs) await assertSucceeds(deleteDoc(d.ref));
    await assertSucceeds(deleteDoc(doc(owner, 'users', OWNER.uid)));
  });

  test('멤버는 탈퇴하며 자신이 받은 초대장을 찾아 지울 수 있지만 남의 초대장은 조회할 수 없다', async () => {
    await seedTransferRoom();
    const partner = googleUser(PARTNER);
    const received = await assertSucceeds(
      getDocs(query(collection(partner, 'invitations'), where('partnerEmail', '==', PARTNER.email)))
    );
    assertEqualCount(received.size, 1);
    for (const d of received.docs) await assertSucceeds(deleteDoc(d.ref));
    await assertFails(getDocs(query(collection(partner, 'invitations'), where('inviterUid', '==', OWNER.uid))));
  });
});

function assertEqualCount(actual, expected) {
  if (actual !== expected) throw new Error(`expected ${expected} documents but got ${actual}`);
}
