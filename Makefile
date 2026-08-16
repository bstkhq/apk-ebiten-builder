.PHONY: test test-android test-device

test:
	./tests/run-java-tests.sh
	./tests/run-template-tests.sh

test-android: test
	./tests/build-android-fixtures.sh

test-device: test-android
	./tests/verify-legacy-device.sh
	./tests/verify-bridge-device.sh
	./tests/verify-back-device.sh
