BUILDER_DIR ?= .build/apk-ebiten-builder
BUILDER_REPO ?= https://github.com/bstkhq/apk-ebiten-builder
# Empty follows the latest commit on the remote default branch.
# Set a release tag or commit to pin the builder.
BUILDER_REF ?=
INCLUDE_PATH ?= $(BUILDER_DIR)/Include.mk

.PHONY: apk-builder-sync
apk-builder-sync:
	@set -eu; \
		builder_dir="$(BUILDER_DIR)"; \
		builder_repo="$(BUILDER_REPO)"; \
		builder_ref="$(BUILDER_REF)"; \
		previous_ref="$$(git -C "$$builder_dir" rev-parse --verify --quiet HEAD 2>/dev/null || true)"; \
		remote_changed=0; \
		if [ -e "$$builder_dir" ]; then \
			if ! git -C "$$builder_dir" rev-parse --is-inside-work-tree >/dev/null 2>&1; then \
				echo "BUILDER_DIR is not a Git checkout: $$builder_dir" >&2; \
				exit 2; \
			fi; \
			if ! current_repo="$$(git -C "$$builder_dir" remote get-url origin 2>/dev/null)"; then \
				echo "BUILDER_DIR has no origin remote: $$builder_dir" >&2; \
				exit 2; \
			fi; \
			if [ "$$current_repo" != "$$builder_repo" ]; then \
				git -C "$$builder_dir" remote set-url origin "$$builder_repo"; \
				remote_changed=1; \
			fi; \
		else \
			mkdir -p "$$(dirname "$$builder_dir")"; \
			git clone --quiet "$$builder_repo" "$$builder_dir"; \
			remote_changed=1; \
		fi; \
		if [ -z "$$builder_ref" ] || [ "$$remote_changed" -eq 1 ]; then \
			git -C "$$builder_dir" fetch --quiet --force --prune --tags origin; \
		fi; \
		if [ -n "$$builder_ref" ]; then \
			if ! resolved_ref="$$(git -C "$$builder_dir" rev-parse --verify --quiet "$$builder_ref^{commit}")"; then \
				git -C "$$builder_dir" fetch --quiet --force --prune --tags origin; \
				if ! resolved_ref="$$(git -C "$$builder_dir" rev-parse --verify --quiet "$$builder_ref^{commit}")"; then \
					echo "BUILDER_REF does not name an available commit or release: $$builder_ref" >&2; \
					exit 2; \
				fi; \
			fi; \
		else \
			git -C "$$builder_dir" remote set-head origin --auto >/dev/null 2>&1; \
			default_ref="$$(git -C "$$builder_dir" symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || true)"; \
			if [ -z "$$default_ref" ]; then \
				echo "Could not resolve the default branch for $$builder_repo" >&2; \
				exit 2; \
			fi; \
			resolved_ref="$$(git -C "$$builder_dir" rev-parse --verify "$$default_ref^{commit}")"; \
		fi; \
		git -C "$$builder_dir" checkout --quiet --detach "$$resolved_ref"; \
		test -f "$(INCLUDE_PATH)"; \
		if [ "$$previous_ref" != "$$resolved_ref" ]; then touch "$(INCLUDE_PATH)"; fi

ifeq ($(MAKE_RESTARTS),)
$(INCLUDE_PATH): apk-builder-sync
endif
$(INCLUDE_PATH):
	@:

include $(INCLUDE_PATH)
