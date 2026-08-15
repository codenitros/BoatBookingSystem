# Makefile for code formatting with Spotless

.PHONY: format check

format:
	mvn -B -V spotless:apply

check:
	mvn -B -V spotless:check
