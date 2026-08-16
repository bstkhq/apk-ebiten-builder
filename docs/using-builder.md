# Selecting an apk-ebiten-builder revision

Put the following bootstrap in the `Makefile` of an Ebiten project. It obtains
`Include.mk` before the rest of the project Makefile is evaluated, then keeps
that checkout at the requested builder revision.

```make
# Application configuration
APP_NAME ?= My Game
APP_ID   ?= games.mycompany.mygame
GO_SRC   ?= $(abspath .)

# Builder source and revision
BUILDER_DIR  ?= .build/apk-ebiten-builder
BUILDER_REPO ?= https://github.com/bstkhq/apk-ebiten-builder
# Empty follows the latest commit on the remote default branch.
# Set a release tag or commit to pin the builder.
BUILDER_REF  ?=
INCLUDE_PATH ?= $(BUILDER_DIR)/Include.mk

export APP_ID

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
```

The recipe uses only POSIX shell syntax. The `MAKE_RESTARTS` guard lets GNU
Make re-read a changed `Include.mk` once without synchronizing the checkout a
second time during the same invocation.

## Selecting a revision

| `BUILDER_REF` value | Result |
| --- | --- |
| Empty (the default) | Fetches and checks out the latest commit on the remote's default branch every time `make` runs. |
| Release tag, such as `v0.1.0` | Checks out the commit identified by that tag in detached-HEAD mode. |
| Commit SHA | Checks out that exact commit in detached-HEAD mode. Prefer a full SHA for an unambiguous, reproducible build. |

Set the ref in the project's `Makefile` to make the choice permanent:

```make
BUILDER_REF ?= v0.1.0
```

Or use it for one command:

```bash
make build BUILDER_REF=v0.1.0
make build BUILDER_REF=0123456789abcdef0123456789abcdef01234567
```

Once a tag or commit has been resolved in the local checkout, a pinned build
can run without contacting the remote. Leaving the ref empty deliberately
requires network access, because it checks for a new default-branch commit.

## Using a fork or mirror

Set `BUILDER_REPO` to any compatible Git repository. When the value changes,
the bootstrap updates the cached checkout's `origin` and fetches from the new
source before resolving `BUILDER_REF`.

```make
BUILDER_REPO ?= https://example.com/your-org/apk-ebiten-builder.git
BUILDER_REF  ?= v0.1.0
```

If the Go `bridge` package is used, keep its module version aligned with the
builder release:

```bash
go get github.com/bstkhq/apk-ebiten-builder/bridge@v0.1.0
```
