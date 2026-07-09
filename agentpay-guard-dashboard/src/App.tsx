import './App.css'

type Decision = 'ALLOW' | 'DENY' | 'REQUIRE_APPROVAL'
type AnchorStatus = 'ANCHORED' | 'PENDING'

type PaymentRequest = {
  id: string
  merchant: string
  resource: string
  amount: string
  decision: Decision
  anchorStatus: AnchorStatus
  txHash: string
  createdAt: string
}

const paymentRequests: PaymentRequest[] = [
  {
    id: 'pay_1de773ab',
    merchant: 'openai',
    resource: 'gpt-api',
    amount: '$10.00',
    decision: 'ALLOW',
    anchorStatus: 'ANCHORED',
    txHash: '0x794c648eae1b85cdd3f25f052221bfba9e1dad434eb35223088604d7b14981dc',
    createdAt: '2026-07-06 10:15',
  },
  {
    id: 'pay_83ad1c02',
    merchant: 'vector-db',
    resource: 'embedding-index',
    amount: '$64.00',
    decision: 'REQUIRE_APPROVAL',
    anchorStatus: 'PENDING',
    txHash: '-',
    createdAt: '2026-07-06 10:21',
  },
  {
    id: 'pay_aa58f1e0',
    merchant: 'blocked-merchant',
    resource: 'premium-api',
    amount: '$120.00',
    decision: 'DENY',
    anchorStatus: 'PENDING',
    txHash: '-',
    createdAt: '2026-07-06 10:24',
  },
]

const selectedRequest = paymentRequests[0]

function StatusBadge({
  value,
}: {
  value: Decision | AnchorStatus
}) {
  return <span className={`status status-${value.toLowerCase().replace('_', '-')}`}>{value}</span>
}

function shortHash(value: string) {
  if (value === '-') {
    return value
  }
  return `${value.slice(0, 10)}...${value.slice(-8)}`
}

function App() {
  return (
    <main className="dashboard-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Local PoC</p>
          <h1>AgentPay Guard</h1>
        </div>
        <div className="topbar-status">
          <span className="status-dot" aria-hidden="true" />
          Hardhat 31337
        </div>
      </header>

      <section className="metrics-grid" aria-label="Audit summary">
        <div className="metric-panel">
          <span className="metric-label">Requests today</span>
          <strong>18</strong>
          <span className="metric-note">3 waiting approval</span>
        </div>
        <div className="metric-panel">
          <span className="metric-label">Anchored</span>
          <strong>12</strong>
          <span className="metric-note">txHash returned</span>
        </div>
        <div className="metric-panel">
          <span className="metric-label">Budget used</span>
          <strong>$184.20</strong>
          <span className="metric-note">$500.00 limit</span>
        </div>
      </section>

      <section className="content-grid">
        <section className="panel request-panel" aria-labelledby="request-list-title">
          <div className="panel-header">
            <div>
              <h2 id="request-list-title">Payment Requests</h2>
              <p>Policy decisions and anchoring state</p>
            </div>
            <button className="secondary-button" type="button">Refresh</button>
          </div>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Merchant</th>
                  <th>Amount</th>
                  <th>Decision</th>
                  <th>Anchor</th>
                </tr>
              </thead>
              <tbody>
                {paymentRequests.map((request) => (
                  <tr key={request.id}>
                    <td>
                      <span className="mono">{request.id}</span>
                      <span className="cell-note">{request.createdAt}</span>
                    </td>
                    <td>
                      <strong>{request.merchant}</strong>
                      <span className="cell-note">{request.resource}</span>
                    </td>
                    <td className="amount">{request.amount}</td>
                    <td><StatusBadge value={request.decision} /></td>
                    <td><StatusBadge value={request.anchorStatus} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="panel detail-panel" aria-labelledby="detail-title">
          <div className="panel-header compact">
            <div>
              <h2 id="detail-title">Request Detail</h2>
              <p className="mono">{selectedRequest.id}</p>
            </div>
            <StatusBadge value={selectedRequest.decision} />
          </div>

          <dl className="detail-list">
            <div>
              <dt>Merchant</dt>
              <dd>{selectedRequest.merchant}</dd>
            </div>
            <div>
              <dt>Resource</dt>
              <dd>{selectedRequest.resource}</dd>
            </div>
            <div>
              <dt>Amount</dt>
              <dd>{selectedRequest.amount}</dd>
            </div>
            <div>
              <dt>Policy</dt>
              <dd>RULE_ALLOW</dd>
            </div>
          </dl>

          <div className="audit-box">
            <div className="audit-row">
              <span>eventHash</span>
              <code>sha256:32805e15...c382ea7</code>
            </div>
            <div className="audit-row">
              <span>chainId</span>
              <code>31337</code>
            </div>
            <div className="audit-row">
              <span>txHash</span>
              <code>{shortHash(selectedRequest.txHash)}</code>
            </div>
          </div>

          <div className="action-row">
            <button className="primary-button" type="button">Copy txHash</button>
            <button className="secondary-button" type="button">Verify hash</button>
          </div>
        </aside>
      </section>
    </main>
  )
}

export default App
