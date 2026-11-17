# Guarded reading-state storage

This is the protocol and adapter boundary for #447 under #432 and roadmap #397.
It extends the [reading-state decisions](reading-state.md) without treating a pure
proposal as permission to write. Room and desktop JDBC implement the same DAO and decision protocol. Production
reader and playback adoption remain separate work.

## Protocol boundary

`ReadingStateDao.prepare` resolves an existing ChapterKey and atomically returns
its current value with an incarnation, explicit-intent revision and store-open
scope. Preparation changes only new protocol metadata. Android starts from its
actual read/first-read fields; old desktop state starts UNKNOWN, not inferred unread.

Explicit read/unread/Next commands carry the prepared binding and a stable command
ID. Visual/audio commands also carry a registered writer session, content observation,
canonical document revision, positive sequence and typed completion evidence.
Registration replaces only the matching writer kind and resets its sequence.
The adapter checks content observations inside the registration/write transaction.
Rendered input and active playback facts remain trusted host inputs; session
matching does not independently prove that a user scrolled or an engine played.

The transaction checks incarnation first, then an existing command receipt. A known
identical receipt returns valueAtCommit and the current value separately, without
writing either. This permits confirmation across reopen or later explicit intents
while the same incarnation still exists. It does not reactivate an old writer.
A changed request under the same ID conflicts. A missing/changed incarnation is
stale even for a known receipt.

For a new command, the store-open scope must be current. Explicit commands compare
the expected intent revision; conflicting owners cannot both commit. Every applied
explicit command advances intent revision, including same-value read/unread, and
retires both automatic sessions. Automatic commands require the current session,
content and intent revision plus a newer per-writer sequence. No timestamp or
fraction chooses ordering. New commands with old observations cannot be rebased
silently onto current intent.

Only applied commands reserve receipts. A below-threshold, disabled or already-read
automatic decision returns NO_CHANGE without storing a receipt or advancing a
sequence. It is not an accepted state mutation. This avoids a receipt row for every
viewport sample. A later explicit unread changes intent revision, so old samples
remain stale. Applied receipt IDs stay reserved for the incarnation; a fresh prepare
after invalidation removes obsolete receipts. Within one incarnation, E applied
explicit intents permit at most E+1 automatic state transitions, so there are at
most 2E+1 receipt rows. Repeated viewport/playback no-ops add none. This is linear
growth with explicit intents, not a fixed storage cap. There is no eviction policy;
future compaction must define which old commands become stale before discarding
receipts. Pure protocol tests check the bound; real adapters check that no-ops add no receipts.

The shared decision returns the result, next protocol record and new receipt.
Adapters commit value, record and receipt together or roll back all three. Replays
must publish current, never valueAtCommit, as the screen's current state. Missing
required JSON fields, unexpected fields, malformed values or unknown versions fail
closed. An absent protocol row after invalidation is normal: prepare then creates
a new incarnation from actual values; it does not treat corrupt JSON as absence.
Request fingerprints preserve exact UTF-16 units, including isolated surrogates.
They cover semantic inputs and projection ordinals; they do not duplicate chapter
bodies or device-local cached image paths.

## Android expansion and legacy writers

DB36 adds reading_states (chapterId, state JSON) and reading_state_receipts
(chapterId, commandId, receipt JSON). Both reference chapters with delete cascade;
receipts do not reference the protocol row. The migration creates empty tables and
identity/legacy-write invalidation triggers. It does not rewrite chapter/history,
body/version, checkpoint, download receipt or membership rows.

Every UPDATE OF read or firstReadAt, including a same-value update, deletes the
protocol row. This makes current manual/bulk/TTS/backup writers revoke prepared
bindings before they are migrated to the facade. Chapter/novel identity changes
also invalidate protocol state. The next prepare issues a new incarnation from the
actual legacy values and removes obsolete receipts. Actual chapter deletion cascades.

The new adapter first reads/validates actual chapter state in its transaction.
When applying a command it writes read/firstRead exactly, which fires the same
invalidation trigger, then restores the decided next protocol record and receipt
before commit. The intermediate missing row is invisible outside the transaction.
Receipts survive that intermediate deletion. Cancellation or a fault rolls back
the chapter fields, invalidation and new protocol together. Word-count backfill
and opened history remain separate; this adapter cannot claim their success.
In particular, it does not call the legacy global word-count backfill. A zero
word count stays zero, even on MarkRead. Before production adoption, the platform
facade must preserve that legacy side effect and report its failure separately;
`Committed` acknowledges only read/first-read plus protocol/receipt persistence.

## Desktop expansion

SQL3 adds the same logical record/receipt tables keyed by ChapterKey with a
novel foreign key. The record stores the new value; old desktop libraries have no
historical completion value. Preparation does not infer one from legacy positions.
Chapter-list refresh deletes records and receipts for removed chapters before a
same-URL recreation can prepare again. Relocking retains completion but invalidates
automatic content authority through the existing generation protocol. Explicit
read/unread needs an existing identity, not a visible/unlocked downloaded body.

SQL1/2 stores upgrade transactionally; older readers must refuse user_version=3.
The existing marker, exclusive store lease, legacy file backup and content codec
remain unchanged. Separate adapter objects in one store can test intent conflicts;
they do not establish concurrent-process acceptance beyond the existing lease.

## Verification and proof boundaries

- Pure protocol/strict codec cases: same-value intents, two prepared owners,
  queued visual/audio versus unread, fresh rebind, sequence/session/content mismatch,
  exact retry after later unread, conflicting IDs, no-op behavior and UTF-16 hashing.
- Real Room/JDBC rollback, cancellation, lost confirmation, reopen and identity ABA;
  raw legacy same-value read updates must invalidate old prepares on Android.
- Frozen DB35/SQL2 upgrade input and exact legacy data preservation; malformed
  records must never be overwritten as if no prior state existed.
- Exact-head CI/review, actual Android ART cases and signed DB36 upgrade/restart/
  downgrade-refusal/recovery evidence. Keep helpers, UI/emulator, phone and real
  process-death/competing-process evidence separate.

Android's 11 native `ReadingStateTest` cases use real Room transactions and a
frozen DB35 schema. Desktop's 12 JDBC cases inspect preserved SQL2 tables and
rows. They include cancellation before commit, an uncertain reply after commit,
identity retirement, corruption rejection and known-receipt confirmation after
reopen. The Android suite also checks two independently opened Room owners.
Desktop retains its exclusive lease; its tests do not claim simultaneous processes.
Hosted results and signed migration snapshots must be recorded separately from
compilation or JVM results.

Before upgrading, close the app and retain a full app-data/store copy, including
the database and downloaded files. The in-app library backup is insufficient.
Older Android DB35 and desktop SQL1/2 builds refuse the expanded schema. Downgrade
requires restoring that coherent backup; changing only the version number or
removing new tables would discard protocol authority and is unsupported.

The production visual/manual/bulk/Next/TTS writers and shared opened-history writer
remain later subissues. This protocol does not claim that all current writers
already use durable intent ordering.

## Completion facade and writer retirement

`ReadingStateFacade` prepares explicit commands with the accepted input timestamp
and a stable ID. Committed read proposals fill a null `firstReadAt` once and
preserve an existing timestamp. Explicit MarkRead and eligible audio may fill it
on an already-read row; visual and Next compatibility checks may instead return
NO_CHANGE. Receipt confirmation never supplies a new timestamp. Automatic commands
retain their registered session, evidence and sequence. Hosts serialize their own accepted explicit intents and retain each
immutable command through uncertain IO or cancellation. Retrying submits that
same command; it never prepares a replacement against newer intent automatically.
The facade does not own a background queue, active playback or a durable outbox.

Submission reports completion confirmation separately from word-count backfill.
Android adoption must supply its existing backfill; desktop explicitly supplies
no legacy effect (`NotApplicable`). Backfill must be idempotent because confirming
a receipt can run it again. A failed backfill is a partial result, not a failed
completion transaction. Eligible audio no-ops still request backfill, as the
legacy audio setter does. Rejected/disabled inputs do not. Cancellation propagates.
The returned `current` value is read after the side effect: a newer explicit
unread committed while backfill waited must not be replaced by the old read result.
A missing current value means the target was removed; historical `valueAtCommit`
is confirmation only. A reconciliation-read failure also requires identical retry.
Checkpoint/compatibility position and opened history remain separate outcomes.

`ReadingStateDao.retire` clears only the exact matching writer kind, binding,
content/document and store scope in one transaction. A superseded, other-owner,
retired or old-incarnation handle cannot revoke a newer writer. Retirement leaves
completion, explicit-intent revision, receipts and the other kind untouched.
Repeated retirement returns false without mutation. Corrupt stored metadata still
fails closed. Known receipts can confirm after retirement; new late samples are
stale. Closing/replacing actual UI/playback authority remains the host's job.

This #452 slice retains DB36/SQL3 and JSON version1. Shared workflow tests and
real Room/JDBC retirement, cancellation, rollback and replay cases are separate
from the production adoption in #451 and physical-device/process-death acceptance.
