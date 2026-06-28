import { expect } from "chai";
import { ethers } from "hardhat";
import { anyValue } from "@nomicfoundation/hardhat-chai-matchers/withArgs";

describe("AuditAnchor", function () {
  it("anchors an event hash", async function () {
    const AuditAnchor = await ethers.getContractFactory("AuditAnchor");
    const auditAnchor = await AuditAnchor.deploy();

    const eventHash = ethers.keccak256(ethers.toUtf8Bytes("agentpay-guard:poc:event:1"));

    await expect(auditAnchor.anchor(eventHash))
      .to.emit(auditAnchor, "Anchored")
      .withArgs(eventHash, await (await ethers.getSigners())[0].getAddress(), anyValue);

    expect(await auditAnchor.isAnchored(eventHash)).to.equal(true);
  });

  it("rejects duplicate event hashes", async function () {
    const AuditAnchor = await ethers.getContractFactory("AuditAnchor");
    const auditAnchor = await AuditAnchor.deploy();

    const eventHash = ethers.keccak256(ethers.toUtf8Bytes("agentpay-guard:poc:event:2"));

    await auditAnchor.anchor(eventHash);
    await expect(auditAnchor.anchor(eventHash)).to.be.revertedWith("eventHash already anchored");
  });
});
