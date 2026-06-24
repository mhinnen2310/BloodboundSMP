# BloodboundSMP Release Checklist

## Release Candidate

- [ ] Work is committed on `release/1.0` and merged from `develop`.
- [ ] `VERSION` and `CHANGELOG.md` match the intended release.
- [ ] `git status --short` is empty.
- [ ] `build.ps1` succeeds with Java 25 bytecode target.
- [ ] All generated plugin jars contain `plugin.yml` and classes.
- [ ] Clean Paper server reaches `Done` on Java 25 without plugin errors.
- [ ] Default, VIP, MVP, Legend and every staff rank pass permission/tab-completion tests.
- [ ] Adminmode isolation, inventory snapshots and rollback tests pass.
- [ ] Economy, QuickSell and AuctionHouse exploit tests pass.
- [ ] Anti-cheat is warnings-only and its review log works.
- [ ] Feature flags can disable every risky launch feature.
- [ ] Resource pack downloads from a public HTTPS URL and both custom items render.
- [ ] Multiplayer BedWars, TNT Run, Spleef and boss tests pass.
- [ ] A complete server backup and rollback rehearsal exist.

## Production Release

- [ ] Merge the approved release into `main`.
- [ ] Tag the commit as `v1.0.0`.
- [ ] Build from that exact clean commit.
- [ ] Stop Paper cleanly and verify port 25565 is closed.
- [ ] Run the guarded deploy command.
- [ ] Start Paper with Java 25 and archive startup diagnostics.
- [ ] Run the smoke tests in `BLOODBOUND_TEST_COMMANDS.md`.
