MVNW ?= ./mvnw

.PHONY: help compile test cov check format package demo clean

help:  ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-12s %s\n", $$1, $$2}'

compile:  ## Compile main and test sources
	$(MVNW) -q -DskipTests compile test-compile

test:  ## Run the test suite
	$(MVNW) -q test

cov:  ## Run tests with the JaCoCo report (HTML under target/site/jacoco)
	$(MVNW) -q verify

format:  ## Apply Spotless formatting
	$(MVNW) -q spotless:apply

check:  ## Format check, Checkstyle, tests and coverage gates
	$(MVNW) -q spotless:check checkstyle:check verify

package:  ## Build the shaded CLI jar (target/syncsafe-0.1.0-all.jar)
	$(MVNW) -q -DskipTests package

demo: package  ## Create fixture tables and run an aligned and a diverged verify
	java -jar target/syncsafe-0.1.0-all.jar demo --dir demo/out

clean:  ## Remove build output and demo artefacts
	$(MVNW) -q clean
	rm -rf demo/out
