import { ethers } from "hardhat";

async function main() {
  const contractAddress = process.env.AUDIT_ANCHOR_ADDRESS;
  const eventHash = process.env.EVENT_HASH;

  if (!contractAddress) {
    throw new Error("AUDIT_ANCHOR_ADDRESS is required");
  }

  if (!eventHash) {
    throw new Error("EVENT_HASH is required");
  }

  const auditAnchor = await ethers.getContractAt("AuditAnchor", contractAddress);
  const tx = await auditAnchor.anchor(eventHash);
  const receipt = await tx.wait();

  console.log(`anchored eventHash=${eventHash}`);
  console.log(`txHash=${receipt?.hash}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
