<!--
THIS FILE IS GENERATED. DO NOT EDIT MANUALLY!
-->

```pravda compile evm --input <sequence> --output <file> --meta-from-ipfs --ipfs-node <string>```

## Description
[THIS COMPILATION MODE IS EXPERIMENTAL]Compile .bin produced by the solc compiler to Pravda VM bytecode. The input files are .bin contract and .abi. The output is a binary Pravda program. By default read from stdin and print to stdout
## Options

|Option|Description|
|----|----|
|`-i`, `--input`|An input file
|`-o`, `--output`|An output file
|`--meta-from-ipfs`|Load metadata from IPFS if necessary. To configure the IPFS node address use "--ipfs-node" parameter.
|`--ipfs-node`|Ipfs node (/ip4/127.0.0.1/tcp/5001 by default).
