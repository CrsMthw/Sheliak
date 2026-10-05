# Project-specific R8 rules.
#
# Intentionally empty for now: every library on the classpath ships its own consumer rules, and
# kotlinx.serialization's compile-time serializers need no reflection keeps. Add a rule here only with a
# comment naming the crash or R8 error it fixes and how to retest without it.
