# sbt-betterzip

`sbt-betterzip` contains a custom implementation of `IO.zip` that uses `commons-compress` and supports extras that the base implementation doesn't, namely, the ability to set the executable flag for files.

## Use

This is almost a drop-in replacement for `IO.zip` from SBT. Calling `BetterZip.zip` with a set of zip entries performs the actual zipping.

`BetterZip` contains some helper implicits that (should) allow this implementation to be a drop-in replacement for `IO.zip`. There's a helper added implicitly for `File` as well (`->*`) which generates an entry mapping that sets the executable bits (`chmod 755`) in the zip.