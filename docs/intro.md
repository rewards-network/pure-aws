---
id: intro
title: Getting Started
---
# Getting Started
## Setup
**Package is not published yet**. Once it is published, you will be able to add this dependency to your project as follows.
```
libraryDependencies += "com.rewardsnetwork" %% "<module-name>" % "@VERSION@"
```

## Usage
The libraries in this repository follow a general architecture pattern you should be aware of.
Each library has a "Pure" client layer, followed by a "Simplified" client layer, and optionally some kind of "Refined" client layer above that (currently only for SQS).
Each one adds enhancements on top of the other, and the base libraries feature both the pure and simplified layers.

For example, here is how you create a pure S3 client:
```scala mdoc:silent
import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.rewardsnetwork.pureaws.s3.PureS3Client
import software.amazon.awssdk.regions.Region
import scala.concurrent.ExecutionContext.global

//Needed for these examples. In your application you should be using `IOApp` and not need to define this.
implicit val cs: ContextShift[IO] = IO.contextShift(global)

val region = Region.US_EAST_1

val s3clientResource: Resource[IO, PureS3Client[IO]] =
  Blocker[IO].flatMap(blocker => PureS3Client.async[IO](blocker, region))
```

The "Pure" clients for each library have Sync/Async variants, based on the underlying AWS client.
Due to API differences, they only resolve to a common subset of available operations between them.
Generally you should prefer the Async clients as they are natively non-blocking, but consider the Sync clients if necessary for semantic or performance reasons.

Each library has a host of simplified clients you can use as well.
The functionality of these clients are optimized to meet specific business needs, and might not be all-encompassing.
If you find yourself using the pure client for any reason, consider thinking of a way it can be simplified and incorporate it into an existing simplified client or create a new specialized one.