.PHONY: all compile build install clean

AAR_OUT := bin/android-libs/game.aar
AAR_DIR := $(dir $(AAR_OUT))
GO_PKG  := github.com/erparts/ebiten-android/mobile
JAVA_PKG := games.bombastik.funtastik.kiosk.corelib
ANDROID_DIR := bin/android

APP_PKG := games.bombastik.funtastik.kiosk
MAIN_ACTIVITY := .MainActivity

all: compile build

compile:
	mkdir -p $(AAR_DIR)
	ebitenmobile bind -target android -javapkg $(JAVA_PKG) -o $(AAR_OUT) $(GO_PKG)

build:
	cd $(ANDROID_DIR) && ./gradlew assembleDebug

install:
	cd $(ANDROID_DIR) && ./gradlew installDebug
	adb shell am start -n $(APP_PKG)/$(MAIN_ACTIVITY)

clean:
	rm -f $(AAR_OUT)
	cd $(ANDROID_DIR) && ./gradlew clean