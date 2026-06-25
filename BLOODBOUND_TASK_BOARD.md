# BloodboundSMP Task Board

This file records active work in a simple visible format. New requests should be added under `Te doen`; finished items move to `Afgerond`.

## Te Doen

- Manual in-game validation of the EndBoss physical ritual with real Boss Shards and Corrupted Hearts.
- Manual in-game validation that sandbox AuctionHouse listings are invisible from the SMP AuctionHouse.
- Manual in-game validation that sandbox QuickSell uses sandbox balance and does not affect EconomyWatch.

## Bezig

- None.

## Afgerond

- Added visible startup/storage backend reporting for SQLite vs fallback properties.
- Added SQLite-first storage with fallback for legacy `PropertiesFile` users.
- Added maintenance mode and guided smoke QA.
- Added staff alerts for captured severe command/server errors.
- Fixed EndBoss ritual item detection to accept Boss Shards and Corrupted Hearts by item model and legacy CustomModelData.
- Changed the EndBoss ritual sacrifice to use real lightning and lethal mob removal before the Ritual Chest appears.
- Added better Ritual Chest missing-item feedback when the chest is broken incomplete.
- Separated sandbox AuctionHouse data from SMP AuctionHouse data.
- Separated sandbox QuickSell wallet behavior from the SMP wallet.
- Made `mitchtest_*` sandbox worlds full-permission test spaces for Admin/Owner/OP.
- Reworked the repository README into a professional GitHub front page.

## Niet Gedaan / Onzeker

- RC1 tag was not recreated because the exact RC1 commit is not known with certainty.
- Runtime folders are kept on disk because this workspace is also the local server. They remain ignored by git.
