.PHONY: test test-android

test:
	./tests/run-java-tests.sh
	./tests/run-template-tests.sh

test-android: test
	./tests/build-android-fixtures.sh
