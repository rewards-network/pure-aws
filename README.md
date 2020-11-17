# Pure AWS
A Scala integrations library for AWS using principles of pure functional programming.
Depends heavily on Cats, Cats Effect, and FS2.

Currently includes the following modules, with more to come:
* `pure-aws-s3`: S3 file sources and sinks
* `pure-aws-s3-testing`: Test helpers to ensure you're using the S3 clients correctly
* `pure-aws-sqs`: Basic and simplified SQS access
* `pure-aws-sqs-refined`: Builds on top of `fs2-sqs` with `refined` integration for type-safe method parameters.

## Install
```
libraryDependencies += "com.rewardsnetwork" %% "<module-name>" % "<latest tag>"
```

## Usage
The libraries in this repository follow a general architecture pattern you should be aware of.
Each library has a "Pure" client layer, followed by a "Simplified" client layer, and optionally some kind of "Refined" client layer above that (currently only for SQS).
Each one adds enhancements on top of the other, and the base libraries feature both the pure and simplified layers.

To create a simplified or pure client, it is recommended to use the `Builders` constructor functions in each library.
For example, here is how you create an S3 and SQS client:
```scala
import com.rewardsnetwork.pureaws.s3.{Builders => S3Builders}
import com.rewardsnetwork.pureaws.sqs.{Builders => SQSBuilders} //Renamed for clarity
import software.amazon.awssdk.regions.Region

Blocker[IO].flatMap { blocker => //Everything needs a Blocker
  val region = Region.US_EAST_1

  val pureS3clientResource = S3Builders.pureClientResource[IO](region, blocker)
  val pureSQSclientResource = SQSBuilders.pureClientResource[IO](region, blocker)
}
```

The "Pure" clients for each library have Sync/Async variants, based on the underlying AWS client.
Due to API differences, they only resolve to a common subset of available operations between them.
Generally you should prefer the Async clients as they are natively non-blocking, but consider the Sync clients if you find you have a certain performance concern.

Each library has a host of simplified clients you can use as well.
The functionality of these clients are optimized to meet specific business needs, and might not be all-encompassing.
If you find yourself using the pure client for any reason, consider thinking of a way it can be simplified and incorporate it into an existing simplified client or create a new specialized one.

These simplified clients are as follows:

### fs2-s3

#### FileOps
Create a `com.rewardsnetwork.fs2.s3.S3FileOps` using one of the supplied `Builders` constructor functions.

```scala
val fileOpsResource: Resource[IO, S3FileOps[IO]] = Blocker[IO].flatMap(Builders.fileOps[IO](region, _))

fileOpsResource.use { fileOps =>

  //Define copy or delete operations manually...
  val copy = fileOps.copyFile("oldBucket", "oldKey", "newBucket", "newKey")
  val delete = fileOps.deleteFile("oldBucket", "oldKey")
  
  //Or use a simplified version as `move` which copies and deletes in sequence
  val move = fileOps.moveFile("oldBucket", "oldKey", "newBucket", "newKey")

  move
} //IO[Unit]
```

#### Sink
Create a `com.rewardsnetwork.fs2.s3.S3FileSink` using one of the supplied `Builders` constructor functions.

```scala
val fileSinkResource: Resource[IO, S3FileSink[IO]] = Blocker[IO].flatMap(Builders.fileSink[IO](region, _))

fileSinkResource.use { fileSink =>
  Stream("hello", "world", "and all who inhabit it")
    .through(fs2.text.encodeUtf8)
    .through(fileSink.writeText(bucket, path))
    .compile
    .drain
} //IO[Unit]
```

#### Source
Create a `com.rewardsnetwork.pureaws.s3.S3FileSource` using one of the supplied `Builders` constructor functions.

```scala
val fileSourceResource: Resource[IO, S3FileSource[IO]] = Blocker[IO].flatMap(Builders.fileSource[IO](region, _))

Stream.resource(fileSourceResource).flatMap(_.readFile("myBucket", "myKey")) //Stream[IO, Byte]
```

### fs2-sqs
The preferred way to use `fs2-sqs` is to pull in the Simple client with an Async backend.
```scala
import com.rewardsnetwork.pureaws.sqs.Builders

val client: Resource[IO, SimpleSqsClient[IO]] = Builders.simpleClientResourceAsync[IO](blocker, region)
client.use { c =>
  c.streamMessages("url-to-my-queue", maxMessages = 10).take(3).compile.drain.as(ExitCode.Success)
}
```

A `SimpleSqsClient` is a more type-safe alternative to dealing with the traditional client, as it creates requests in the background and parses their responses.

Using the `refined` library, we can validate our function parameters at compile-time instead of making the request ourselves and assuming it is correct.
To take advantage of this, import the `pureaws-sqs-refined` library.
`refined` support is currently limited to method parameters and some returned types.
To see all available refined types used by this library, see `package.scala` in the `pureaws-sqs-refined` module.

If you want to access the raw client, `Builders` has those available, as well as a `PureSqsClient` that acts as a thin wrapper around it, making all of its functions pure using best practices in thread management.

When using SQS, be sure to delete messages after you are done processing.
To make this easier on yourself, use the `autoDelete` method on each SQS message.
This method takes an `F[Boolean]` predicate as an argument, and if it returns true, it will delete the message, otherwise do nothing.

## Testing
Some libraries may have testing modules implemented separately as-needed.
Presently there is just `fs2-s3-testing`.

### fs2-s3-testing
There is a small backend for testing the simplified clients, such as `S3FileSink` and `S3FileSource`, available as `S3Backend.inMemory`.
It is designed to be used with the `Test` clients, and they will throw exceptions for certain common operations such as when you try to get a file that does not exist.

All components are available in the `testing` package in `pureaws-s3-testing`.

```scala
import com.rewardsnetwork.pureaws.s3.testing._
import com.rewardsnetwork.pureaws.s3._
import fs2.Stream
import fs2.text._

//First, make a backend
val program = S3Backend.inMemory[IO].flatMap { backend =>
  //Plug the backend into each component you test
  val sink: S3FileSink[IO] = TestS3FileSink(backend)

  //Components that share a backend will be testable together as one system
  val source: S3FileSource[IO] = TestS3FileSource(backend)

  val exampleFile = "Hello world!"

  val writeFile = Stream(exampleFile).through(fs2.text.encodeUtf8).through(sink.writeText("bucket", "key"))

  val readFile = source
    .readFile("bucket", "key")
    .through(fs2.text.decodeUtf8)
    .stdOutLines //prints out the file contents

  (writeFile >> readFile).compile.drain
}

program.unsafeRunSync() //Hello world!
```
