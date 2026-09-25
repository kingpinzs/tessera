L13-1 JVM tests (specific tests only, the QA ruling of 2026-09-25)

Command: ./gradlew :app:testDebugUnitTest --tests app.tileshell.cortana.SessionResultRegistryTest --tests app.tileshell.cortana.ReminderPhotosTest

Red before the fix: red-before-fix.log (exit 1: SessionResultRegistry, CortanaResults and ReminderPhotos do not exist).
Green after the fix: TEST-*.xml — SessionResultRegistryTest 7/7, ReminderPhotosTest 5/5 (exit 0).

Mutation check (each mutation applied to the fixed source, the two classes run, the source restored byte-identical):
  M1 aside-first      Tess steps aside before the launch goes out          SessionResultRegistryTest 4 of 7 fail (exit 1)
  M3 dispatch-first   the result is dispatched before Tess comes back      SessionResultRegistryTest 2 of 7 fail (exit 1)
  M2 no-confinement   copyFile accepts any file: path                      ReminderPhotosTest 2 of 5 fail (exit 1)
Logs: mutation-*.log.
