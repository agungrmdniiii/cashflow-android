# Google Drive public access readiness

Verified 26 October 2026 in Google Cloud Console for project `duit-aing-android-2026`. No login credentials are recorded here.

## Current status

- Audience: **External**, publishing status **In production**, with the **Back to testing** button visible after the owner explicitly approved access for all Google accounts.
- Branding: homepage, privacy URL and authorized domain saved successfully; Console displayed **Branding changes saved!**.
- Homepage: https://agungrmdniiii.github.io/cashflow-android/
- Privacy: https://agungrmdniiii.github.io/cashflow-android/privacy.html
- Authorized domain: `agungrmdniiii.github.io`.
- Search Console confirmed website ownership using the public HTML meta tag. Keep that tag in the homepage.
- Data Access: `drive.appdata` is the sole configured non-sensitive scope. Sensitive and restricted scope tables are empty.
- Production status is verified. On 26 October 2026, an existing Google account different from the former sole tester connected successfully on the Pixel 10 Pro emulator, using APK142. The user completed the recovery-password and Google consent steps. Opening the Drive restore list completed successfully with "Belum ada cadangan pada akun ini." A subsequent opt-in synthetic migration audit uploaded, listed and downloaded a 257031-byte encrypted example archive, then restored those downloaded bytes in an isolated database on a separate fresh emulator. Partial-download cancellation and wrong-password/truncated-archive guards passed. Larger-photo transfer attempts encountered a download timeout and an upload connection reset; large backups are not yet verified stable. See `docs/DEVICE_RESTORE_AUDIT.md`. No real-user backup or live-ledger replacement was performed. The user confirmed that Google went directly to permission consent without the "Google hasn't verified this app" warning or an Advanced bypass. This observation covers the account and consent attempt tested, rather than every possible account/device policy.
- Screenshot: `app/build/end-user-audit/google-production-live.png`.

## Publication boundary

The owner approved publishing only the information/privacy pages, followed by explicit approval for OAuth Production. No APK was released for these configuration changes.

Pages uses native branch deployment from `codex/public-info-pages`, root folder. Final site-only commit: `240aa1f5589fc4a008fb3d50062bdf4ede28935e`. Deployment succeeded; both public URLs returned HTTP 200 with correct UTF-8 content and the homepage verification tag. Main remained `ab82c56871fa17d31803bb936b30d7a494fe0365` at publication.

Local sources: `site/index.html`, `site/privacy.html`, `site/site.css`. The unused manual-only Pages workflow draft was removed. No JavaScript, analytics, external fonts or forms are included.

Branding initially failed to load on 25 October; it loaded and saved successfully on 26 October before publishing OAuth.

Android APK142 is unchanged: SHA256 `B4835AD4FB7E7496EE74A4C51C560FE7E03842CC391A3F48DB033CA12952B188`.

References:
- https://support.google.com/cloud/answer/15549049
- https://support.google.com/cloud/answer/13807376
- https://developers.google.com/workspace/drive/api/guides/appdata
- https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site
