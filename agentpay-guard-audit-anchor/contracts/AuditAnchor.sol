// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

contract AuditAnchor {
    event Anchored(bytes32 indexed eventHash, address indexed actor, uint256 timestamp);

    mapping(bytes32 => uint256) public anchoredAt;

    function anchor(bytes32 eventHash) external {
        require(eventHash != bytes32(0), "eventHash is required");
        require(anchoredAt[eventHash] == 0, "eventHash already anchored");

        anchoredAt[eventHash] = block.timestamp;
        emit Anchored(eventHash, msg.sender, block.timestamp);
    }

    function isAnchored(bytes32 eventHash) external view returns (bool) {
        return anchoredAt[eventHash] != 0;
    }
}
