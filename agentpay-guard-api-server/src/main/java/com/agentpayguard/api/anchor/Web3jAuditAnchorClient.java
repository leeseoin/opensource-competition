package com.agentpayguard.api.anchor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

/**
 * Spring Boot API server에서 web3j로 AuditAnchor 컨트랙트를 직접 호출하는 구현이다.
 * Hardhat script를 거치지 않고 RPC URL, contract address, private key 설정만으로 eventHash를 온체인 기록한다.
 */
@Component
@ConditionalOnProperty(prefix = "agentpay.audit-anchor", name = "enabled", havingValue = "true")
public class Web3jAuditAnchorClient implements AuditAnchorClient {

    private static final String ANCHOR_FUNCTION = "anchor";

    private final Web3j web3j;
    private final String contractAddress;
    private final String privateKey;
    private final BigInteger gasLimit;

    public Web3jAuditAnchorClient(
            @Value("${agentpay.audit-anchor.rpc-url}") String rpcUrl,
            @Value("${agentpay.audit-anchor.contract-address}") String contractAddress,
            @Value("${agentpay.audit-anchor.private-key}") String privateKey,
            @Value("${agentpay.audit-anchor.gas-limit:300000}") BigInteger gasLimit
    ) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.contractAddress = requireConfigured(contractAddress, "contract address");
        this.privateKey = requireConfigured(privateKey, "private key");
        this.gasLimit = gasLimit;
    }

    /**
     * Java의 "sha256:{hex}" eventHash를 Solidity bytes32 인자로 변환한 뒤 anchor(bytes32) 트랜잭션을 보낸다.
     * 트랜잭션 receipt가 성공이면 txHash를 포함한 ANCHORED 결과를 반환한다.
     */
    @Override
    public AnchorResult anchor(String eventHash) {
        try {
            BigInteger chainId = web3j.ethChainId().send().getChainId();
            Credentials credentials = Credentials.create(privateKey);
            TransactionManager transactionManager = new RawTransactionManager(web3j, credentials, chainId.longValue());

            Function function = new Function(
                    ANCHOR_FUNCTION,
                    List.of(new Bytes32(toBytes32(eventHash))),
                    List.of()
            );
            String encodedFunction = FunctionEncoder.encode(function);
            BigInteger gasPrice = gasPrice();

            EthSendTransaction transaction = transactionManager.sendTransaction(
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    encodedFunction,
                    BigInteger.ZERO
            );

            if (transaction.hasError()) {
                throw new IllegalStateException("Failed to send audit anchor transaction: "
                        + transaction.getError().getMessage());
            }

            String txHash = transaction.getTransactionHash();
            TransactionReceipt receipt = waitForReceipt(txHash);
            if (!receipt.isStatusOK()) {
                throw new IllegalStateException("Audit anchor transaction failed: " + txHash);
            }

            return new AnchorResult("ANCHORED", chainId.toString(), contractAddress, txHash);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to connect to audit anchor RPC", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to anchor audit event hash", e);
        }
    }

    /**
     * 로컬 Hardhat node의 현재 gas price를 조회해 raw transaction 전송값으로 사용한다.
     */
    private BigInteger gasPrice() throws IOException {
        EthGasPrice ethGasPrice = web3j.ethGasPrice().send();
        return ethGasPrice.getGasPrice();
    }

    /**
     * anchor 트랜잭션이 블록에 포함될 때까지 짧게 polling한다.
     */
    private TransactionReceipt waitForReceipt(String txHash) throws Exception {
        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 1000, 30);
        return receiptProcessor.waitForTransactionReceipt(txHash);
    }

    /**
     * API server의 hash 표현인 "sha256:{64 hex}" 또는 "0x{64 hex}"를 Solidity bytes32로 정규화한다.
     */
    private byte[] toBytes32(String eventHash) {
        String normalized = eventHash;
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length());
        }
        if (!normalized.startsWith("0x")) {
            normalized = "0x" + normalized;
        }

        byte[] bytes = Numeric.hexStringToByteArray(normalized);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("eventHash must be a 32-byte SHA-256 hash");
        }
        return bytes;
    }

    /**
     * anchoring이 켜진 상태에서 필수 설정이 빠졌을 때 애플리케이션 시작 단계에서 명확히 실패시킨다.
     */
    private String requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Audit anchor " + name + " is required when anchoring is enabled");
        }
        return value;
    }
}
