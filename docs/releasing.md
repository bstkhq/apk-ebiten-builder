# Signed release builds

`Include.mk` produces debug APKs. Add the following target to a consuming
project's `Makefile` to build a signed release APK:

```make
KEYSTORE_PATH ?= $(ROOT_DIR)/release.keystore
KEYSTORE_PASS ?=
KEY_ALIAS ?=
KEY_PASS ?= $(KEYSTORE_PASS)

APK_RELEASE := $(ANDROID_DIR)/app/build/outputs/apk/release/app-release.apk

release: generate compile
	$(Q)test -f "$(KEYSTORE_PATH)" || { echo "Keystore not found"; exit 1; }
	$(Q)test -n "$(KEYSTORE_PASS)" || { echo "KEYSTORE_PASS empty"; exit 1; }
	$(Q)test -n "$(KEY_ALIAS)"     || { echo "KEY_ALIAS empty"; exit 1; }
	$(call GRADLE_RUN,assembleRelease \
		-Pandroid.injected.signing.store.file=$(KEYSTORE_PATH) \
		-Pandroid.injected.signing.store.password=$(KEYSTORE_PASS) \
		-Pandroid.injected.signing.key.alias=$(KEY_ALIAS) \
		-Pandroid.injected.signing.key.password=$(KEY_PASS))

.PHONY: release
```

Run it with the keystore details supplied by the release environment:

```bash
make release KEYSTORE_PASS='***' KEY_ALIAS=upload KEY_PASS='***'
```

The generated APK is at
`.build/android/app/build/outputs/apk/release/app-release.apk` by default.
