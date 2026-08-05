# Privacy Policy — Shelfie

**Last updated:** [DATE]
**App:** Shelfie: Screenshot Finder (`com.pitchcode.shelfie`)
**Developer:** [YOUR LEGAL NAME / COMPANY]
**Contact:** [YOUR SUPPORT EMAIL]

> ⚠️ **Template — requires legal review before publication.** This is a technically accurate description of the app's behaviour as specified, written to satisfy Google Play's User Data policy. It is **not legal advice**. Have a lawyer review it before you publish, especially regarding India's Digital Personal Data Protection Act 2023, the GDPR, and the CCPA. You must also keep it accurate — if you later add analytics, ads, or cloud sync, this document must be updated **in the same release**.

---

## The short version

Shelfie does not collect your data. It does not have permission to access the internet, so it cannot send your information anywhere. Everything it does happens on your phone.

---

## 1. What Shelfie accesses

**Your screenshots and images.** With your permission, Shelfie reads image files on your device to make them searchable.

**What it does with them:** it extracts the text visible in each image using on-device text recognition, works out a category, and saves that text and category in a private database inside the app's own storage area on your phone.

**What it does not do:** it does not upload your images. It does not upload the extracted text. It does not copy, move, or modify your original image files unless you explicitly ask it to delete them.

## 2. What Shelfie collects and shares

**Nothing.**

We collect no personal information. We do not operate a server that receives your data. We have no user accounts, so we never ask for your name, email address, or phone number. We do not use advertising networks, analytics services, crash-reporting services, or any third-party SDK that transmits data.

## 3. Why we can make that promise credibly

Shelfie is built without the `INTERNET` permission. On Android, an app cannot open a network connection without it. This is enforced by the operating system, not by our good intentions.

You can verify this yourself by inspecting the app's declared permissions — for example with `adb shell dumpsys package com.pitchcode.shelfie`, or any app-info viewer that lists declared permissions. `android.permission.INTERNET` will not be there.

Note that Android's own **Settings → Apps → Permissions** screen only shows *runtime* permissions you can toggle, so it will not list `INTERNET` either way. The absence is visible in the app's full declared-permission list, which is what the methods above show.

## 4. Permissions we request, and why

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` | To read your screenshots so they can be indexed and searched. Without it, Shelfie can only work on images you hand-pick individually. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | To support Android's partial-access option, where you grant access to specific images rather than all of them. |
| `READ_EXTERNAL_STORAGE` (Android 12 and older only) | The older equivalent of the permission above, on versions of Android that predate the granular media permissions. |
| `POST_NOTIFICATIONS` | To show occasional storage-cleanup suggestions. Optional — you can decline or turn it off, and the app works fully without it. |

### Permissions added by Android's background-work library

Shelfie uses Android's standard WorkManager library to index older screenshots
while your phone is idle and charging. That library declares four permissions of
its own, so you will see them listed in Android's app info screen:

| Permission | What it does here |
|---|---|
| `WAKE_LOCK` | Lets a background indexing batch finish without the CPU sleeping mid-way. |
| `ACCESS_NETWORK_STATE` | Lets the library *check* whether a network exists. It cannot send or receive data. |
| `RECEIVE_BOOT_COMPLETED` | Lets unfinished indexing resume after you restart your phone. |
| `FOREGROUND_SERVICE` | Declared by the library. Shelfie does not run any foreground service. |

We want to be precise about `ACCESS_NETWORK_STATE`, because at a glance it looks
network-related. On Android, observing connectivity and *using* the network are
two separate permissions. Without `INTERNET`, an app cannot open a connection no
matter what else it holds. So this permission cannot be used to transmit your
data, and Shelfie does not use it to do anything at all — it comes along with the
scheduling library.

We deliberately do **not** request: internet access, location, contacts, camera,
microphone, phone, or SMS.

### If you decline photo access
Shelfie still works. You can select individual images through Android's system photo picker and Shelfie will index those. We call this Limited Mode, and every feature works within it.

## 5. Where your data is stored

In your app's private storage directory on your device, which other apps cannot read. Nothing is stored on any server, because there is no server.

Your extracted text index may be included in **Android's own system backup** if you have device backup enabled in your Google account settings. That backup is operated by Google under [Google's Privacy Policy](https://policies.google.com/privacy), not by us, and you control it in your device settings. You can exclude Shelfie from backup in your Android backup settings.

## 6. How long we keep it

For as long as you keep the app installed. You can remove it at any time:

- **Settings → Clear index** — deletes all extracted text and categories, leaving your original images untouched
- **Uninstall the app** — Android deletes the app's entire private storage, removing everything

Because we hold no copy of your data, uninstalling is a complete and irreversible deletion.

## 7. Your rights

Rights such as access, correction, deletion, portability, and objection exist under laws including India's DPDP Act 2023, the EU/UK GDPR, and the CCPA/CPRA.

In Shelfie's case these are satisfied directly on the device, because we are not holding your data:

- **Access / portability** — Settings → Export my data produces a file of your index
- **Deletion** — Settings → Clear index, or uninstall
- **Correction** — edit any category or create a rule in the app
- **Objection / withdrawal of consent** — revoke the photo permission in Android settings at any time

There is no request for us to process, because we have nothing to hand over. If you have a question about this, contact us at **[YOUR SUPPORT EMAIL]**.

## 8. Purchases

Shelfie offers a single one-time in-app purchase. It is processed by **Google Play Billing**. We never see or receive your payment details. Google's handling of that transaction is covered by [Google's Privacy Policy](https://policies.google.com/privacy). We store only a local flag on your device recording that the purchase was made.

There is no subscription and no recurring charge.

## 9. Children

Shelfie is not directed at children. It does not knowingly collect information from anyone, including children, because it does not collect information at all.

## 10. Optional AI features

On some higher-end Android devices, Shelfie may offer extra features (such as plain-language search) that use Android's built-in on-device AI system service. These run **on your device**. They are optional and off by default. They do not send your screenshots to us or to any third party.

## 11. Changes to this policy

If we change how the app handles data, we will update this policy and the "Last updated" date, and we will update our Google Play Data Safety declaration in the same release. Material changes will be highlighted in the app.

## 12. Contact

**[YOUR LEGAL NAME / COMPANY]**
Email: **[YOUR SUPPORT EMAIL]**
Address: **[REQUIRED for GDPR/DPDP compliance — a business address must be provided]**

---

### Publication checklist
- [ ] Replace every `[BRACKETED]` placeholder
- [ ] Reviewed by a lawyer
- [ ] Hosted at a public **HTTPS** URL that does not require login
- [ ] URL entered in Play Console → App content → Privacy policy
- [ ] Same URL linked from Settings inside the app
- [ ] Content matches the Data Safety form exactly
- [ ] Re-verify on every release that it still describes actual behaviour
