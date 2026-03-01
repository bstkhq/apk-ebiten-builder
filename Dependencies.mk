# Android + Ebitenmobile dependencies installer (no package manager; installs into $HOME/Android/Sdk)
# - Installs Android SDK cmdline-tools + platform-tools + platforms;android-35 + build-tools;35.0.0 + NDK + CMake
# - Installs ebitenmobile via `go install`
#
# Targets:
#   install_dependencies   -> everything (SDK + packages + ebitenmobile)
#   info_sdk              -> quick sanity check
#   list_sdk              -> sdkmanager --list
#   update_sdk            -> sdkmanager --update
#   accept_licenses       -> sdkmanager --licenses
#   clean_sdk             -> rm -rf SDK dir
#
# Assumptions:
# - Java is already installed and on PATH.
# - Go is installed and on PATH (for ebitenmobile).

SHELL := /bin/bash

# ----- Config -----
ANDROID_SDK_ROOT ?= $(HOME)/Android/Sdk
ANDROID_HOME ?= $(ANDROID_SDK_ROOT)

# Official cmdline-tools "latest" zip (Linux)
CMDLINE_TOOLS_ZIP ?= https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip

# Match your Gradle config
COMPILE_SDK ?= 35
BUILD_TOOLS ?= 35.0.0

# Required by gomobile/ebitenmobile bind
NDK_VER ?= 26.3.11579264
CMAKE_VER ?= 3.22.1

# ebitenmobile install target
EBITENMOBILE_MOD ?= github.com/hajimehoshi/ebiten/v2/cmd/ebitenmobile@latest

# Derived
SDKMANAGER := $(ANDROID_SDK_ROOT)/cmdline-tools/latest/bin/sdkmanager
SDKMANAGER_ENV := ANDROID_SDK_ROOT="$(ANDROID_SDK_ROOT)" ANDROID_HOME="$(ANDROID_HOME)" \
	PATH="$(ANDROID_SDK_ROOT)/cmdline-tools/latest/bin:$(ANDROID_SDK_ROOT)/platform-tools:$$PATH"

# Pretty logs (only prefix colored green)
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

.PHONY: install_dependencies install_ebitenmobile \
        info_sdk list_sdk update_sdk accept_licenses clean_sdk

install_dependencies: install_android_sdk install_android_packages install_ebitenmobile
	$(call LOG,All dependencies installed)

install_android_sdk:
	$(call LOG,Prepare SDK directories at $(ANDROID_SDK_ROOT))
	@mkdir -p "$(ANDROID_SDK_ROOT)/cmdline-tools"
	@mkdir -p "$(HOME)/.android" && touch "$(HOME)/.android/repositories.cfg"

	$(call LOG,Download Android command-line tools)
	@tmp="$$(mktemp -d)"; \
		curl -L -o "$$tmp/cmdline-tools.zip" "$(CMDLINE_TOOLS_ZIP)"; \
		unzip -q "$$tmp/cmdline-tools.zip" -d "$(ANDROID_SDK_ROOT)/cmdline-tools"; \
		rm -rf "$(ANDROID_SDK_ROOT)/cmdline-tools/latest"; \
		mv "$(ANDROID_SDK_ROOT)/cmdline-tools/cmdline-tools" "$(ANDROID_SDK_ROOT)/cmdline-tools/latest"; \
		rm -rf "$$tmp"

install_android_packages:
	$(call LOG,Accept Android SDK licenses)
	@$(SDKMANAGER_ENV) bash -lc 'yes | "$(SDKMANAGER)" --licenses'

	$(call LOG,Install SDK packages: platform-tools, android-$(COMPILE_SDK), build-tools $(BUILD_TOOLS), NDK $(NDK_VER), CMake $(CMAKE_VER))
	@$(SDKMANAGER_ENV) bash -lc '"$(SDKMANAGER)" \
		"platform-tools" \
		"platforms;android-$(COMPILE_SDK)" \
		"build-tools;$(BUILD_TOOLS)" \
		"ndk;$(NDK_VER)" \
		"cmake;$(CMAKE_VER)"'

	$(call LOG,SDK environment hints)
	@echo "ANDROID_SDK_ROOT=$(ANDROID_SDK_ROOT)"
	@echo 'Add to your shell rc:'
	@echo '  export ANDROID_SDK_ROOT="$(ANDROID_SDK_ROOT)"'
	@echo '  export ANDROID_HOME="$$ANDROID_SDK_ROOT"'
	@echo '  export PATH="$$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$$ANDROID_SDK_ROOT/platform-tools:$$PATH"'

install_ebitenmobile:
	$(call LOG,Install ebitenmobile via go install)
	@bash -lc 'set -euo pipefail; \
	  command -v go >/dev/null || { echo "ERROR: go not found in PATH"; exit 2; }; \
	  go install "$(EBITENMOBILE_MOD)"; \
	  echo "Installed: $$(go env GOPATH)/bin/ebitenmobile"; \
	  echo "Ensure GOPATH/bin is in PATH:"; \
	  echo "  export PATH=\"$$(go env GOPATH)/bin:$$PATH\"";'

accept_licenses:
	$(call LOG,Accept Android SDK licenses)
	@$(SDKMANAGER_ENV) bash -lc 'yes | "$(SDKMANAGER)" --licenses'

update_sdk:
	$(call LOG,Update installed SDK packages)
	@$(SDKMANAGER_ENV) bash -lc '"$(SDKMANAGER)" --update'

list_sdk:
	$(call LOG,List SDK packages)
	@$(SDKMANAGER_ENV) bash -lc '"$(SDKMANAGER)" --list'

info_sdk:
	$(call LOG,SDK info)
	@echo "ANDROID_SDK_ROOT = $(ANDROID_SDK_ROOT)"
	@echo "ANDROID_HOME     = $(ANDROID_HOME)"
	@echo "sdkmanager       = $(SDKMANAGER)"
	@$(SDKMANAGER_ENV) bash -lc '"$(SDKMANAGER)" --version'
	@$(SDKMANAGER_ENV) bash -lc 'command -v adb >/dev/null && adb version || echo "adb not found (install platform-tools)"'
	@echo
	@echo "Expected:"
	@echo "  $(ANDROID_SDK_ROOT)/platforms/android-$(COMPILE_SDK)"
	@echo "  $(ANDROID_SDK_ROOT)/build-tools/$(BUILD_TOOLS)"
	@echo "  $(ANDROID_SDK_ROOT)/ndk/$(NDK_VER)"
	@echo "  $(ANDROID_SDK_ROOT)/cmake/$(CMAKE_VER)"
	@echo "  $$(go env GOPATH 2>/dev/null)/bin/ebitenmobile"

clean_sdk:
	$(call LOG,Remove SDK directory: $(ANDROID_SDK_ROOT))
	@rm -rf "$(ANDROID_SDK_ROOT)"

# Internal phony targets
.PHONY: install_android_sdk install_android_packages