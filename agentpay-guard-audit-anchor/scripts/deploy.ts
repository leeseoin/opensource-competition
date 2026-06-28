import { ethers } from "hardhat";

async function main() {
  const AuditAnchor = await ethers.getContractFactory("AuditAnchor");
  const auditAnchor = await AuditAnchor.deploy();
  await auditAnchor.waitForDeployment();

  console.log(`AuditAnchor deployed to ${await auditAnchor.getAddress()}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
