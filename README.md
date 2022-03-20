# Payment subscription service for Come Back Alive foundation

## How to develop

Copy `template.config.mk` into `.config.mk` and create `comebacksub` database in
local PostgreSQL:

```
$ createdb comebacksub
```

Install clojure cli tools (`brew install clojure` or similar) and after that
running `make` should work.
