# Learnings

- The repository uses `gradlew` but it may have CRLF line endings which cause issues in Linux environments. Run `sed -i 's/\r$//' gradlew` to fix.
- The test suite has existing failures (`GoogleNewsUrlDecoderTest`, `RssFeedParserTest`) related to unresolved references, possibly due to environment configuration or missing dependencies/files.
- `ArticleDetailScreen.kt` has compilation errors due to missing parameters in `Article` constructor (or `Source` constructor) usage. This suggests recent changes to `Article` or `Source` data models were not propagated to UI code.
- Unit testing `CoroutineWorker` requires `androidx.work:work-testing` or manual instantiation. In this case, manual instantiation worked fine since `WorkerParameters` could be mocked (or ignored).
- `Log.d` usage in unit tests requires `testOptions { unitTests.isReturnDefaultValues = true }` in `build.gradle` to avoid "Method not mocked" exceptions.
