# Pure AWS
A Scala integrations library for AWS using principles of pure functional programming.
Depends heavily on Cats, Cats Effect, and FS2.

Currently includes the following modules, with more to come:
* `pure-aws-s3`: S3 file sources and sinks
* `pure-aws-s3-testing`: Test helpers to ensure you're using the S3 clients correctly
* `pure-aws-sqs`: Basic and simplified SQS access
* `pure-aws-sqs-refined`: Builds on top of `pure-aws-sqs` with `refined` integration for type-safe method parameters.

## Setup
**Package is not published yet**. Once it is published, you will be able to add this dependency to your project as follows.
```
libraryDependencies += "com.rewardsnetwork" %% "<module-name>" % "<latest tag>"
```

## License
This project is licensed under the Apache License V2.0.

Copyright 2020 Rewards Network Establishment Services.

Please see the LICENSE in this repository for more information.

## Usage
The libraries in this repository follow a general architecture pattern you should be aware of.
Each library has a "Pure" client layer, followed by a "Simplified" client layer, and optionally some kind of "Refined" client layer above that (currently only for SQS).
Each one adds enhancements on top of the other, and the base libraries feature both the pure and simplified layers.

For example, here is how you create an S3 and SQS client:
```scala
import cats.effect.{Blocker, IO}
import com.rewardsnetwork.pureaws.s3.PureS3Client
import com.rewardsnetwork.pureaws.sqs.PureSqsClient
import software.amazon.awssdk.regions.Region

Blocker[IO].flatMap { blocker => //Everything needs a Blocker
  val region = Region.US_EAST_1

  val pureS3clientResource = PureS3Client.async[IO](blocker, region)
  val pureSQSclientResource = PureSqsClient.async[IO](blocker, region)
}
```

The "Pure" clients for each library have Sync/Async variants, based on the underlying AWS client.
Due to API differences, they only resolve to a common subset of available operations between them.
Generally you should prefer the Async clients as they are natively non-blocking, but consider the Sync clients if necessary for semantic or performance reasons.

Each library has a host of simplified clients you can use as well.
The functionality of these clients are optimized to meet specific business needs, and might not be all-encompassing.
If you find yourself using the pure client for any reason, consider thinking of a way it can be simplified and incorporate it into an existing simplified client or create a new specialized one.

These simplified clients are as follows:

### S3

#### S3ObjectOps
Perform basic operations on S3 objects:

```scala
import com.rewardsnetwork.pureaws.S3ObjectOps

val opsResource: Resource[IO, S3ObjectOps[IO]] = Blocker[IO].flatMap(S3ObjectOps.async[IO](_, region))

opsResource.use { ops =>

  //Define copy or delete operations manually...
  val copy = ops.copyFile("oldBucket", "oldKey", "newBucket", "newKey")
  val delete = ops.deleteFile("oldBucket", "oldKey")
  
  //Or use a simplified version as `move` which copies and deletes in sequence
  val move = ops.moveFile("oldBucket", "oldKey", "newBucket", "newKey")

  move
} //IO[Unit]
```

#### Sink
Write S3 objects using S3 (multipart not currently supported):

```scala
import com.rewardsnetwork.pureaws.S3Sink

val sinkResource: Resource[IO, S3Sink[IO]] = Blocker[IO].flatMap(S3Sink.async[IO](_, region))

fileSinkResource.use { sink =>
  Stream("hello", "world", "and all who inhabit it")
    .through(fs2.text.encodeUtf8)
    .through(sink.writeText(bucket, path)) //Can also write raw bytes, and set custom content type
    .compile
    .drain
} //IO[Unit]
```

#### Source
Stream S3 objects from S3 as bytes:

```scala
import com.rewardsnetwork.pureaws.S3Source

val sourceResource: Resource[IO, S3Source[IO]] = Blocker[IO].flatMap(S3Source.async[IO](_, region))

Stream.resource(fileSourceResource).flatMap { source =>
  //Stream bytes from an object
  val byteStream = source.readObject("myBucket", "myKey") //Stream[IO, Byte]
  
  //Get an object's metadata and a stream of bytes
  val metadataAndByteStream = source.readObjectWithMetadata("myBucket", "myKey") //IO[(Map[String, String], Stream[IO, Byte])]

  ???
}
```

### SQS
The preferred way to use `pureaws-sqs` is to pull in the Simple client with an Async backend.

```scala
import com.rewardsnetwork.pureaws.sqs.SimpleSqsClient

val client: Resource[IO, SimpleSqsClient[IO]] = SimpleSqsClient.async[IO](blocker, region)
client.use { c =>
  c.streamMessages("url-to-my-queue", maxMessages = 10).take(3).compile.drain.as(ExitCode.Success)
}
```

A `SimpleSqsClient` is a more type-safe alternative to dealing with the traditional client, as it creates requests in the background and parses their responses.

Using the `refined` library, we can validate our function parameters at compile-time instead of making the request ourselves and assuming it is correct.
To take advantage of this, import the `pureaws-sqs-refined` library.
`refined` support is currently limited to method parameters and some returned types.
To see all available refined types used by this library, see `package.scala` in the `pureaws-sqs-refined` module.

When using SQS, be sure to delete messages after you are done processing.
To make this easier on yourself, use the `autoDelete` method on each SQS message.
This method takes an `F[Boolean]` predicate as an argument, and if it returns true, it will delete the message, otherwise do nothing.

## Testing
Some libraries may have testing modules implemented separately as-needed.
Presently there is just `pureaws-s3-testing`.

### S3 Testing
There is a small backend for testing the simplified clients, such as `S3Sink` and `S3Source`, available as `S3TestingBackend.inMemory`.
It is designed to be used with the `Test` clients, and they will throw exceptions for certain common operations such as when you try to get a file that does not exist.

All components are available in the `testing` package in `pureaws-s3-testing`.

```scala
import com.rewardsnetwork.pureaws.s3.testing._
import com.rewardsnetwork.pureaws.s3._
import fs2.Stream
import fs2.text._

//First, make a backend
val program = S3TestingBackend.inMemory[IO].flatMap { backend =>
  //Plug the backend into each component you test
  val sink: S3Sink[IO] = TestS3Sink(backend)

  //Components that share a backend will be testable together as one system
  val source: S3Source[IO] = TestS3Source(backend)

  val exampleText = "Hello world!"

  val writeText = Stream(exampleText).through(fs2.text.encodeUtf8).through(sink.writeText("bucket", "key"))

  val readText = source
    .readObject("bucket", "key")
    .through(fs2.text.decodeUtf8)
    .stdOutLines //prints out the file contents

  (writeFile >> readText).compile.drain
}

program.unsafeRunSync() //Hello world!
```
