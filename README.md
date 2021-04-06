# Overview

This is a Concourse resource to delete a GitHub releases and their corresponding tags.

# Building

To run tests:

```shell
make test
```

To push the Docker image:

```shell
make push-docker-image
```

# Usage

## Source Configuration

* `owner`: *Required.* The GitHub user or organization name for the repository
  that the releases are in.
* `repository`: *Required.* The repository name that contains the releases.
* `access_token`: *Required.* API token used for all requests. 

## Behaviour

### `check`: Does nothing. Should not be used.

### `in`: Does nothing. Should not be used.

### `out`: Delete GitHub releases.

### Parameters

* `tag_filter`: *Required.* A Java regex to target the to-be-deleted releases.
* `keep_last_n`: *Required.* The number of releases to keep.

# Examples

```yaml
# declare the resource type
---
resource_types:
  - name: github-release-delete
    type: docker-image
    source:
      repository: pivotalrabbitmq/github-release-delete-resource
      tag: latest

# declare a repository with releases to delete
resources:
  - name: rabbitmq-java-tools-dev-delete
    type: github-release-delete
    source:
      owner: rabbitmq
      repository: rabbitmq-java-tools-binaries-dev
      access_token: token

# in job definition
  - put: rabbitmq-java-tools-dev-delete
    params:
      tag_filter: '^v-stream-perf-test-0.1.0-SNAPSHOT-[0-9]{8}-[0-9]{6}$'
      keep_last_n: 2
```

# License and Copyright

(c) 2021 VMware, Inc. or its affiliates.

This package, the Concourse GitHub Release Delete Resource, is licensed
under the Mozilla Public License 2.0 ("MPL").

See [LICENSE](./LICENSE).