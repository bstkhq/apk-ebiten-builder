# Configuration variables
APP_NAME ?= Ebiten Android
APP_ID ?= games.funtastik.kiosk
MAIN_ACTIVITY ?= .MainActivity
GO_PKG ?= mobile
GO_SRC ?=
VERSION ?= v1.0.0
ROOT_DIR ?= $(abspath .)

# Logging / verbosity
DEBUG ?= 0
MAKEFLAGS += --no-print-directory

ifeq ($(DEBUG),0)
  Q := @
else
  Q :=
endif

# Required variables
ifeq ($(strip $(GO_SRC)),)
  $(error GO_SRC is empty.)
endif
ifeq ($(strip $(ANDROID_SDK_ROOT)),)
  $(error ANDROID_SDK_ROOT is empty.)
endif

# --- Color: make only the ">>>" green, rest default (terminal white) ---
NO_COLOR ?=
TPUT_OK := $(shell command -v tput >/dev/null 2>&1 && echo 1 || echo 0)

ifeq ($(NO_COLOR),1)
  PFX_G :=
  PFX_R :=
else ifeq ($(TPUT_OK),1)
  PFX_G := $(shell tput setaf 2)$(shell tput bold)
  PFX_R := $(shell tput sgr0)
else
  PFX_G := \033[1;32m
  PFX_R := \033[0m
endif

define LOG
	@printf "%b>>>%b %s\n" "$(PFX_G)" "$(PFX_R)" "$(1)"
endef

# Internal variables
SHELL := /bin/bash
ANDROID_SRC := $(ROOT_DIR)/android
ANDROID_DIR := $(ROOT_DIR)/.build/android

APP_ID_PATH := $(subst .,/,$(APP_ID))
JAVA_SRC_ROOT := $(ANDROID_DIR)/app/src/main/java
JAVA_DST_DIR := $(JAVA_SRC_ROOT)/$(APP_ID_PATH)

AAR_PATH := $(ANDROID_DIR)/app/libs/game.aar
AAR_DIR := $(dir $(AAR_PATH))
JAVA_PKG ?= $(APP_ID).corelib

APK_DEBUG := $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk

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
TEMPLATE_VARS := APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY \
	ANDROID_SDK_ROOT VERSION VERSION_CODE
export APP_NAME APP_ID GO_PKG JAVA_PKG MAIN_ACTIVITY ANDROID_SDK_ROOT VERSION VERSION_CODE

# Which files are considered "text templates"
TEMPLATE_FILE_GLOBS := -name "*.gradle" -o -name "*.properties" \
	-o -name "*.xml" -o -name "*.java" -o -name "*.kt" -o -name "*.kts" \
	-o -name "*.toml" -o -name "*.txt" -o -name "*.md"

# Build perl substitution expressions from TEMPLATE_VARS.
# Escape only replacement-sensitive chars: \, &, $ (NOT dots)
# NOTE: avoid $] interpolation by using (\\|&|\$) instead of a [...] class ending with $]
PERL_SUBS := $(foreach v,$(TEMPLATE_VARS),-pe '$$r=$$ENV{$(v)}//""; $$r=~s/(\\\\|&|\\$$)/\\\\$$1/g; s/\@\@$(v)\@\@/$$r/g;')

# Gradle control:
# - DEBUG=1: show gradle output
# - DEBUG=0: capture ALL gradle output (stdout+stderr) to a log, show only on failure
GRADLE_LOG := $(ANDROID_DIR)/.make-gradle.log
GRADLE_BASE_FLAGS :=
ifeq ($(DEBUG),0)
  GRADLE_BASE_FLAGS += -q --console=plain --warning-mode=none
endif

define GRADLE_RUN
	$(Q)bash -lc 'set -euo pipefail; \
	  cd "$(ANDROID_DIR)"; \
	  if [[ "$(DEBUG)" == "0" ]]; then \
	    : > "$(GRADLE_LOG)"; \
	    if ! ./gradlew $(GRADLE_BASE_FLAGS) $(1) >"$(GRADLE_LOG)" 2>&1; then \
	      echo ""; \
	      echo "---- Gradle failed; last 200 lines from $(GRADLE_LOG) ----"; \
	      tail -n 200 "$(GRADLE_LOG)"; \
	      exit 2; \
	    fi; \
	  else \
	    ./gradlew $(1); \
	  fi'
endef

all: clean build install

info:
	$(call LOG,Configuration summary)
	@echo "    APP_NAME      : $(APP_NAME)"
	@echo "    APP_ID        : $(APP_ID)"
	@echo "    MAIN_ACTIVITY : $(MAIN_ACTIVITY)"
	@echo "    JAVA_PKG      : $(JAVA_PKG)"
	@echo "    GO_SRC        : $(GO_SRC)"
	@echo "    ANDROID_SRC   : $(ANDROID_SRC)"
	@echo "    ANDROID_DIR   : $(ANDROID_DIR)"
	@echo "    VERSION       : $(VERSION)"
	@echo "    VERSION_CODE  : $(VERSION_CODE)"
	@echo "    AAR (rel)     : $(AAR_PATH_REL)"
	@echo "    APK (rel)     : $(APK_DEBUG_REL)"
	@echo "    DEBUG         : $(DEBUG)"
	@echo "    GRADLE_LOG    : $(GRADLE_LOG)"

generate: $(ANDROID_DIR)

$(ANDROID_DIR):
	$(call LOG,Generating Android project source code)
	$(Q)mkdir -p $(ANDROID_DIR)
	$(Q)rsync -a --delete \
		--exclude ".gradle/" \
		--exclude "build/" \
		--exclude "**/build/" \
		$(ANDROID_SRC)/ $(ANDROID_DIR)/
	$(Q)find "$(ANDROID_DIR)" -type f \( $(TEMPLATE_FILE_GLOBS) \) -print0 | \
		xargs -0 perl -0777 -i $(PERL_SUBS)
	$(Q)mkdir -p "$(JAVA_DST_DIR)"
	$(Q)find "$(JAVA_SRC_ROOT)" -type f \( -name "*.java" -o -name "*.kt" \) -print0 | \
		while IFS= read -r -d '' f; do \
			if grep -qE '^[[:space:]]*package[[:space:]]+'"$${APP_ID//./\\.}"'[[:space:]]*;' "$$f"; then \
				base="$$(basename "$$f")"; \
				if [[ "$$f" != "$(JAVA_DST_DIR)/"* ]]; then \
					mv -f "$$f" "$(JAVA_DST_DIR)/$$base"; \
				fi; \
			fi; \
		done
	$(Q)find "$(JAVA_SRC_ROOT)" -type d -empty -delete

compile: $(AAR_PATH)

$(AAR_PATH):
	$(call LOG,Compiling AAR library from golang source code)
	$(Q)mkdir -p $(AAR_DIR)
	$(Q)cd "$(GO_SRC)" && \
		ebitenmobile bind -target android -javapkg $(JAVA_PKG) -o "$(AAR_PATH)" .

build: generate compile
	$(call LOG,Building debug APK file)
	$(call GRADLE_RUN,tasks)
	$(call GRADLE_RUN,assembleDebug)
	@echo -n "    Output File: "
	@realpath -m --relative-to="$(ROOT_DIR)" "$(APK_DEBUG)"

install:
	$(call LOG,Installing debug APK to all conected devices)
	$(call GRADLE_RUN,installDebug)
	$(call LOG,Executing APK)
	$(Q)adb shell am start -n $(APP_ID)/$(MAIN_ACTIVITY) >> /dev/null

clean:
	$(call LOG,Removing build directory)
	$(Q)rm -rf $(ANDROID_DIR)

clean_arr:
	$(call LOG,Cleaning up compiled aar file)
	$(Q)rm -f $(AAR_PATH)

.PHONY: all info generate compile build install clean clean_arr print_apk