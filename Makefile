SHELL := /bin/bash
ROOT_DIR := $(abspath .)

# Configuration variables
APP_NAME ?= Ebiten Android
APP_ID ?= games.funtastik.kiosk
MAIN_ACTIVITY ?= .MainActivity
GO_PKG ?= mobile
GO_SRC ?= /home/mcuadros/workspace/go/src/github.com/erparts/go-uikit/example/android

# Internal variables
ANDROID_SRC := $(ROOT_DIR)/bin/android
ANDROID_DIR := $(ROOT_DIR)/.build/android

APP_ID_PATH := $(subst .,/,$(APP_ID))
JAVA_SRC_ROOT := $(ANDROID_DIR)/app/src/main/java
JAVA_DST_DIR := $(JAVA_SRC_ROOT)/$(APP_ID_PATH)

ARR_PATH := $(ANDROID_DIR)/app/libs/game.aar
AAR_DIR := $(dir $(ARR_PATH))
JAVA_PKG ?= $(APP_ID).corelib

# Names of placeholders to replace in templates: @@VAR@@
TEMPLATE_VARS := APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY ANDROID_SDK_ROOT
export APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY

# Which files are considered "text templates"
TEMPLATE_FILE_GLOBS := -name "*.gradle" -o -name "*.properties" \
	-o -name "*.xml" -o -name "*.java" -o -name "*.kt" -o -name "*.kts" \
	-o -name "*.toml" -o -name "*.txt" -o -name "*.md"

# Build perl substitution expressions from TEMPLATE_VARS.
# Escape only replacement-sensitive chars: \, &, $ (NOT dots)
# NOTE: avoid $] interpolation by using (\\|&|\$) instead of a [...] class ending with $]
PERL_SUBS := $(foreach v,$(TEMPLATE_VARS),-pe '$$r=$$ENV{$(v)}//""; $$r=~s/(\\\\|&|\\$$)/\\\\$$1/g; s/\@\@$(v)\@\@/$$r/g;')

all: generate compile build

generate: $(ANDROID_DIR)

$(ANDROID_DIR):
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

compile: $(ARR_PATH)

$(ARR_PATH):
	mkdir -p $(AAR_DIR)
	cd "$(GO_SRC)" && \
		ebitenmobile bind -target android -javapkg $(JAVA_PKG) -o "$(ARR_PATH)" .

build:
	cd "$(ANDROID_DIR)" && ./gradlew tasks && ./gradlew assembleDebug

install:
	cd "$(ANDROID_DIR)" && ./gradlew installDebug
	adb shell am start -n $(APP_ID)/$(MAIN_ACTIVITY)

clean:
	rm -rf $(ANDROID_DIR)

clean_arr:
	rm -f $(ARR_PATH)

.PHONY: all compile prepare_android build install run clean clean_android