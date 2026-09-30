const navigation = [
  "Dashboard",
  "Employees",
  "Products & Prices",
  "Transactions",
  "Daily Report",
  "Monthly Report",
];

export default function DashboardPage() {
  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">Honest Milk · Admin</div>
        <nav className="nav" aria-label="Admin navigation">
          {navigation.map((item, index) => (
            <a className={`nav-item ${index === 0 ? "active" : ""}`} href="#" key={item}>
              {item}
            </a>
          ))}
        </nav>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <h1>Dashboard</h1>
            <p>Honest Milk - Dodla Employee Store</p>
          </div>
          <strong>Admin</strong>
        </header>

        <section className="stats">
          <article className="stat"><div className="stat-label">Employees Served Today</div><div className="stat-value">0</div></article>
          <article className="stat"><div className="stat-label">Products Issued Today</div><div className="stat-value">0</div></article>
          <article className="stat"><div className="stat-label">Volume Today</div><div className="stat-value">0 L</div></article>
          <article className="stat"><div className="stat-label">Value Today</div><div className="stat-value">₹0</div></article>
        </section>

        <section className="panel">
          <h2>Today&apos;s activity</h2>
          <p className="subtitle">Live transaction data will appear here after Supabase authentication and queries are connected.</p>
        </section>
      </main>
    </div>
  );
}
