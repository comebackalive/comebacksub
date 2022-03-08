# Payment subscription service

## How to develop

Copy `template.config.mk` into `.config.mk` and create `uapatron` database in
local PostgreSQL:

```
$ createdb uapatron
```

Install clojure cli tools (`brew install clojure` or similar) and after that
running `make` should work.
