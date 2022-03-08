VERSION = $(shell cat VERSION)

# minus means "include but do not fail"
-include ./.config.mk

run:
	clj -M:dev

ancient:
	clojure -M:dev:ancient

upgrade:
	clojure -M:dev:ancient --upgrade

uber:
	clojure -Srepro -T:build uber

clean:
	rm -rf target
