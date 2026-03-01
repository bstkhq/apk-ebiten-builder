APP_NAME ?= Ebiten Android
APP_ID ?= games.funtastik.kiosk
MAIN_ACTIVITY ?= .MainActivity


SHELL := /bin/bash


.PHONY: all compile prepare_android build install run clean clean_android

# Android template source and generated project
ANDROID_SRC := bin/android
ANDROID_DIR := .build/android

# ---- Template variables (override from CLI if needed) ----
JAVA_PKG ?= $(APP_ID).corelib

# ---- Derived paths ----
APP_ID_PATH := $(subst .,/,$(APP_ID))
JAVA_SRC_ROOT := $(ANDROID_DIR)/app/src/main/java
JAVA_DST_DIR := $(JAVA_SRC_ROOT)/$(APP_ID_PATH)

# ---- Paths / inputs ----
AAR_OUT := $(ANDROID_DIR)/app/libs/game.aar
AAR_DIR := $(dir $(AAR_OUT))
GO_PKG  := github.com/erparts/ebiten-android/mobile

# Names of placeholders to replace in templates: @@VAR@@
TEMPLATE_VARS := APP_NAME APP_ID JAVA_PKG MAIN_ACTIVITY

# Export so perl can read them as $ENV{VAR}
export APP_NAME APP_ID JAVA_PKG MAIN_ACTIVITY

# Which files are considered "text templates"
TEMPLATE_FILE_GLOBS := -name "*.gradle" -o -name "*.properties" \
	-o -name "*.xml" -o -name "*.java" -o -name "*.kt" -o -name "*.kts" \
	-o -name "*.toml" -o -name "*.txt" -o -name "*.md"

# Build perl substitution expressions from TEMPLATE_VARS.
# Escape only replacement-sensitive chars: \, &, $ (NOT dots)
# NOTE: avoid $] interpolation by using (\\|&|\$) instead of a [...] class ending with $]
PERL_SUBS := $(foreach v,$(TEMPLATE_VARS),-pe '$$r=$$ENV{$(v)}//""; $$r=~s/(\\\\|&|\\$$)/\\\\$$1/g; s/\@\@$(v)\@\@/$$r/g;')

all: prepare_android compile build

prepare_android:
	mkdir -p $(ANDROID_DIR)
	rsync -a --delete \
		--exclude ".gradle/" \
		--exclude "build/" \
		--exclude "**/build/" \
		$(ANDROID_SRC)/ $(ANDROID_DIR)/
	# Template replace @@VAR@@ -> value
	find "$(ANDROID_DIR)" -type f \( $(TEMPLATE_FILE_GLOBS) \) -print0 | \
		xargs -0 perl -0777 -i $(PERL_SUBS)
	# Ensure Java/Kotlin source paths match the package name (APP_ID)
	mkdir -p "$(JAVA_DST_DIR)"
	find "$(JAVA_SRC_ROOT)" -type f \( -name "*.java" -o -name "*.kt" \) -print0 | \
		while IFS= read -r -d '' f; do \
			if grep -qE '^[[:space:]]*package[[:space:]]+'"$${APP_ID//./\\.}"'[[:space:]]*;' "$$f"; then \
				base="$$(basename "$$f")"; \
				if [[ "$$f" != "$(JAVA_DST_DIR)/"* ]]; then \
					mv -f "$$f" "$(JAVA_DST_DIR)/$$base"; \
				fi; \
			fi; \
		done
	find "$(JAVA_SRC_ROOT)" -type d -empty -delete

compile:
	mkdir -p $(AAR_DIR)
	ebitenmobile bind -target android -javapkg $(JAVA_PKG) -o $(AAR_OUT) $(GO_PKG)

build:
	cd $(ANDROID_DIR) && ./gradlew tasks && ./gradlew assembleDebug

install: build
	cd $(ANDROID_DIR) && ./gradlew installDebug
	adb shell am start -n $(APP_ID)/$(MAIN_ACTIVITY)

run:
	adb shell am start -n $(APP_ID)/$(MAIN_ACTIVITY)

clean: clean_android
	rm -f $(AAR_OUT)

clean_android:
	rm -rf $(ANDROID_DIR)