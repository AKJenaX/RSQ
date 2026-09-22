import React, { useMemo, useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Download, Search } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { currency } from "../lib/mock-data";
import { useFinance } from "../hooks/useFinance";
import { downloadCsv } from "../utils/csv";
import { formatTimestamp } from "../utils/formatters";

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

export function DonationsPage() {
  const { donations, loadState } = useFinance();
  const validDonations = donations.filter(d => ['VERIFIED', 'ALLOCATED', 'COMPLETED'].includes(d.status));
  const totalDonations = validDonations.reduce((acc, d) => acc + (d.amount || 0), 0);
  const thisMonthDonations = validDonations.filter(d => {
    const date = new Date(d.createdAt || 0);
    const now = new Date();
    return date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
  }).reduce((acc, d) => acc + (d.amount || 0), 0);
  const pendingDonations = donations.filter(d => d.status === "PENDING").reduce((acc, d) => acc + (d.amount || 0), 0);

  const financeTrend = Array.from({ length: 6 }).map((_, i) => {
    const d = new Date();
    d.setMonth(d.getMonth() - (5 - i));
    const monthStr = d.toLocaleDateString("en-US", { month: "short" });
    const monthStart = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
    const monthEnd = new Date(d.getFullYear(), d.getMonth() + 1, 0, 23, 59, 59, 999).getTime();
    const monthTotal = validDonations.filter(d => {
      return d.createdAt >= monthStart && d.createdAt <= monthEnd;
    }).reduce((acc, d) => acc + (d.amount || 0), 0);
    return { day: monthStr, amount: monthTotal };
  });
  const [query, setQuery] = useState("");
  const [type, setType] = useState("All");
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [searchParams] = useSearchParams();
  const donationId = searchParams.get('donationId');

  useEffect(() => {
    if (donationId && loadState === 'success') {
      setExpandedId(donationId);
      setTimeout(() => {
        const el = document.getElementById(`donation-${donationId}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 100);
    }
  }, [donationId, loadState]);
  
  const rows = useMemo(
    () =>
      donations.filter(
        (d) =>
          (type === "All" || d.donationType === type.toUpperCase()) &&
          ((d.donorName || d.donorType || "") + d.donationId).toLowerCase().includes(query.toLowerCase()),
      ),
    [donations, query, type],
  );
  const total = rows.reduce((s, d) => s + (d.amount || 0), 0);

  return (
    <>
      <PageHeader
        title="Donations Ledger"
        subtitle="Contributions received into relief funds"
        actions={
          <button onClick={() => downloadCsv('rsq-authority-donations.csv', ['Reference', 'Donor', 'Type', 'Created at', 'Status', 'Amount'], donations.map((donation) => [donation.donationId, donation.donorName || donation.donorType, donation.donationType, donation.createdAt ? new Date(donation.createdAt).toISOString() : '', donation.status, donation.amount]))} className="inline-flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-secondary">
            <Download className="h-4 w-4" /> Export CSV
          </button>
        }
      />
      {loadState === 'permission-denied' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          You do not have permission to view donation records.
        </p>
      )}
      {loadState === 'error' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Donation records could not be loaded. Please check your connection.
        </p>
      )}
      {(loadState === 'success' || loadState === 'empty') && donations.length === 0 && <p className="rounded-md border border-border bg-muted/20 px-3 py-2 text-sm text-muted-foreground">No donations have been received yet.</p>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Received (Sep)" value={currency(thisMonthDonations)} hint="vs August" />
        <StatCard label="Pending Clearance" value={currency(pendingDonations)} hint="in transit" />
        <StatCard label="Active Donors" value={Array.from(new Set(donations.map(d => d.donationId))).length.toString()} hint="this quarter" />
        <StatCard label="Avg. Gift" value={currency(donations.length > 0 ? totalDonations / donations.length : 0)} hint="individual donors" />
      </div>

      <Panel title="Donation Inflow" description="Received against spend, last 6 months">
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={financeTrend}>
              <defs>
                <linearGradient id="gDon" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="var(--color-chart-3)" stopOpacity={0.45} />
                  <stop offset="100%" stopColor="var(--color-chart-3)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis dataKey="day" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={60} tickFormatter={(v) => currency(v)} />
              <Tooltip contentStyle={tooltipStyle} formatter={(v: any) => currency(v as number)} />
              <Area type="monotone" dataKey="amount" stroke="var(--color-chart-3)" fill="url(#gDon)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Panel>

      <Panel title="Transactions" description={`${rows.length} records - ${currency(total)} total`}>
        <div className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search donor or reference"
              className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:border-ring"
            />
          </div>
          <select
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
          >
            {["All", "Cash", "Goods", "Services", "Online"].map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th className="pb-2 font-medium">Reference</th>
                <th className="pb-2 font-medium">Donor</th>
                <th className="pb-2 font-medium">Type</th>
                <th className="pb-2 font-medium">Date</th>
                <th className="pb-2 font-medium">Status</th>
                <th className="pb-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
              <tbody className="divide-y divide-border">
                {rows.map((d) => (
                  <React.Fragment key={d.donationId}>
                    <tr 
                      id={`donation-${d.donationId}`}
                      className={`cursor-pointer hover:bg-accent/40 transition-colors ${expandedId === d.donationId ? 'bg-primary/5 ring-1 ring-inset ring-primary' : ''}`}
                      onClick={() => setExpandedId(expandedId === d.donationId ? null : d.donationId)}
                    >
                    <td className="py-3 font-mono text-xs text-muted-foreground">{d.donationId}</td>
                    <td className="py-3 font-medium">{d.donorName || d.donorType}</td>
                    <td className="py-3 text-muted-foreground">{d.donationType}</td>
                    <td className="py-3 text-muted-foreground">
                      {formatTimestamp(d.createdAt)}
                    </td>
                    <td className="py-3"><StatusBadge label={d.status} /></td>
                    <td className="py-3 text-right font-medium tabular-nums">{currency(d.amount || 0)}</td>
                  </tr>
                  {expandedId === d.donationId && (
                    <tr className="bg-muted/20">
                      <td colSpan={6} className="px-4 py-4">
                        <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
                          <div>
                            <p className="font-semibold text-foreground">Transaction ID</p>
                            <p className="font-mono text-xs text-muted-foreground">{d.paymentId || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="font-semibold text-foreground">Order ID</p>
                            <p className="font-mono text-xs text-muted-foreground">{d.orderId || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="font-semibold text-foreground">Donor User ID</p>
                            <p className="font-mono text-xs text-muted-foreground">{d.userId || 'N/A'}</p>
                          </div>
                          <div>
                            <p className="font-semibold text-foreground">Payment Date</p>
                            <p className="text-muted-foreground">{d.date || 'N/A'}</p>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>
    </>
  );
}
