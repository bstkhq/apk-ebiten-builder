# Configuration variables
APP_NAME ?= Ebiten Android
APP_ID ?= games.funtastik.kiosk
MAIN_ACTIVITY ?= .MainActivity
GO_PKG ?= mobile
GO_SRC ?=
VERSION ?= v1.0.0
ROOT_DIR ?= $(abspath .)

# Required variables
ifeq ($(strip $(GO_SRC)),)
  $(error GO_SRC is empty.)
endif
ifeq ($(strip $(ANDROID_SDK_ROOT)),)
  $(error ANDROID_SDK_ROOT is empty.)
endif

# Internal variables
SHELL := /bin/bash
ANDROID_SRC := $(ROOT_DIR)/android
ANDROID_DIR := $(ROOT_DIR)/.build/android

APP_ID_PATH := $(subst .,/,$(APP_ID))
JAVA_SRC_ROOT := $(ANDROID_DIR)/app/src/main/java
JAVA_DST_DIR := $(JAVA_SRC_ROOT)/$(APP_ID_PATH)

ARR_PATH := $(ANDROID_DIR)/app/libs/game.aar
AAR_DIR := $(dir $(ARR_PATH))
JAVA_PKG ?= $(APP_ID).corelib

# Permissive: extract first 4 integers found in VERSION (ignore everything else),
# fill missing with 0, then format: major + minor(2) + patch(2) + extra(2)
VERSION_CODE := $(shell bash -lc '\
  set -euo pipefail; \
  v="$(VERSION)"; \
  mapfile -t nums < <(printf "%s" "$$v" | grep -oE "[0-9]+" | head -n 4); \
  major="$${nums[0]:-0}"; \
  minor="$${nums[1]:-0}"; \
  patch="$${nums[2]:-0}"; \
  extra="$${nums[3]:-0}"; \
  printf "%d%02d%02d%02d\n" "$$major" "$$minor" "$$patch" "$$extra"; \
')

# Names of placeholders to replace in templates: @@VAR@@
TEMPLATE_VARS := APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY  \
	ANDROID_SDK_ROOT VERSION VERSION_CODE

export APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY VERSION VERSION_CODE

# Which files are considered "text templates"
TEMPLATE_FILE_GLOBS := -name "*.gradle" -o -name "*.properties" \
	-o -name "*.xml" -o -name "*.java" -o -name "*.kt" -o -name "*.kts" \
	-o -name "*.toml" -o -name "*.txt" -o -name "*.md"

# Build perl substitution expressions from TEMPLATE_VARS.
# Escape only replacement-sensitive chars: \, &, $ (NOT dots)
# NOTE: avoid $] interpolation by using (\\|&|\$) instead of a [...] class ending with $]
PERL_SUBS := $(foreach v,$(TEMPLATE_VARS),-pe '$$r=$$ENV{$(v)}//""; $$r=~s/(\\\\|&|\\$$)/\\\\$$1/g; s/\@\@$(v)\@\@/$$r/g;')

all: clean build install

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

build: generate compile
	cd "$(ANDROID_DIR)" && ./gradlew tasks && ./gradlew assembleDebug

install:
	cd "$(ANDROID_DIR)" && ./gradlew installDebug
	adb shell am start -n $(APP_ID)/$(MAIN_ACTIVITY)

clean:
	rm -rf $(ANDROID_DIR)

clean_arr:
	rm -f $(ARR_PATH)

.PHONY: all compile prepare_android build install run clean clean_android