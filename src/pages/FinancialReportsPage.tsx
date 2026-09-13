import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { FileText } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { useFinance } from "../hooks/useFinance";
import { useAuth } from '../hooks/useAuth';
import { generateFinancialReport } from '../services/financeService';
import { formatTimestamp } from '../utils/formatters';
import { useState } from 'react';

const currency = (n: number) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

export function FinancialReportsPage() {
  const { funds, donations, expenses, financialReports, error } = useFinance();
  const { user } = useAuth();
  const [generating, setGenerating] = useState(false);
  const [period, setPeriod] = useState('Current Month');
  
  const totalAllocated = funds.reduce((acc, f) => acc + (f.allocatedAmount || 0), 0);
  const totalUtilized = funds.reduce((acc, f) => acc + (f.utilizedAmount || 0), 0);

  const realFinanceTrend = Array.from({ length: 6 }).map((_, i) => {
    const d = new Date();
    d.setMonth(d.getMonth() - (5 - i));
    const monthStr = d.toLocaleDateString("en-US", { month: "short" });
    const monthStart = new Date(d.getFullYear(), d.getMonth(), 1).getTime();
    const monthEnd = new Date(d.getFullYear(), d.getMonth() + 1, 0, 23, 59, 59, 999).getTime();

    const monthDonations = donations
      .filter(d => ['VERIFIED', 'ALLOCATED', 'COMPLETED'].includes(d.status) && d.createdAt >= monthStart && d.createdAt <= monthEnd)
      .reduce((acc, d) => acc + (d.amount || 0), 0);
      
    const monthExpenses = expenses
      .filter(e => e.status === 'PAID' && e.paidAt && e.paidAt >= monthStart && e.paidAt <= monthEnd)
      .reduce((acc, e) => acc + (e.amount || 0), 0);
      
    return { month: monthStr, donations: monthDonations, expenses: monthExpenses };
  });

  const income = realFinanceTrend.reduce((s, m) => s + m.donations, 0);
  const spend = realFinanceTrend.reduce((s, m) => s + m.expenses, 0);

  return (
    <>
      <PageHeader
        title="Financial Reports"
        subtitle="Statements, acquittals and donor accountability"
        actions={
          <div className="flex items-center gap-2">
            <select className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none" value={period} onChange={(e) => setPeriod(e.target.value)} disabled={generating}>
              <option>Current Month</option>
              <option>Previous Month</option>
              <option>Last 3 Months</option>
              <option>Last 6 Months</option>
            </select>
            <button onClick={async () => {
              if (!user) return;
              setGenerating(true);
              const snapshot = {
                income,
                allocated: totalAllocated,
                expenditure: spend,
                netPosition: income - spend,
                fundCount: funds.length,
                fundBalances: funds.map(f => ({
                  fundId: f.fundId || f.id || '',
                  purpose: f.purpose || '',
                  available: (f.allocatedAmount || 0) - (f.utilizedAmount || 0)
                }))
              };
              try { await generateFinancialReport(`RSQ Financial Report - ${period}`, period, user.uid, snapshot); } catch (e) { console.error(e); }
              setGenerating(false);
            }} disabled={generating} className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50">
              <FileText className="h-4 w-4" /> {generating ? 'Generating...' : 'Generate report'}
            </button>
          </div>
        }
      />
      {error && <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">Financial records could not be loaded from Firestore.</p>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Income (6 mo)" value={currency(income)} hint="all sources" />
        <StatCard label="Expenditure (6 mo)" value={currency(spend)} hint="programme spend" />
        <StatCard label="Net Position" value={currency(income - spend)} hint="carried forward" />
        <StatCard label="Programme Ratio" value={`${Math.round((totalUtilized / Math.max(totalAllocated, 1)) * 100)}%`} hint="spend reaching field" />
      </div>

      <Panel title="Income vs Expenditure" description="Rolling six months">
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={realFinanceTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis dataKey="month" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={60} tickFormatter={(v) => currency(v as number)} />
              <Tooltip contentStyle={tooltipStyle} formatter={(v: any) => currency(v as number)} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line type="monotone" dataKey="donations" stroke="var(--color-chart-3)" strokeWidth={2.5} dot={{ r: 3 }} />
              <Line type="monotone" dataKey="expenses" stroke="var(--color-chart-4)" strokeWidth={2.5} dot={{ r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Panel>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="GENERATED FINANCIAL REPORTS" description="Audit records of generated financial summaries" className="lg:col-span-2">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th className="pb-2 font-medium">Reference</th>
                  <th className="pb-2 font-medium">Report</th>
                  <th className="pb-2 font-medium">Period</th>
                  <th className="pb-2 font-medium">Generated</th>
                  <th className="pb-2 font-medium">Owner</th>
                  <th className="pb-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {financialReports.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-sm text-muted-foreground">
                      No financial reports generated yet.
                    </td>
                  </tr>
                ) : (
                  financialReports.map(report => (
                    <tr key={report.id} className="hover:bg-accent/40 transition-colors">
                      <td className="py-3 font-mono text-xs text-muted-foreground">{report.reportId}</td>
                      <td className="py-3 font-medium">{report.name}</td>
                      <td className="py-3 text-muted-foreground">{report.period}</td>
                      <td className="py-3 text-muted-foreground">{formatTimestamp(report.generatedAt)}</td>
                      <td className="py-3 font-mono text-xs text-muted-foreground">{report.generatedBy}</td>
                      <td className="py-3"><StatusBadge label={report.status} /></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title="Fund Balances" description="Closing position by programme">
          <ul className="space-y-4 text-sm">
            {funds.map((f) => (
              <li key={f.fundId} className="border-b border-border pb-4 last:border-0 last:pb-0">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold">{f.purpose || f.fundId}</span>
                  <span className="text-xs text-muted-foreground tabular-nums">Budget: {currency(f.totalAmount)}</span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-xs">
                  <div className="bg-muted/30 p-2 rounded">
                    <p className="text-muted-foreground mb-1">Allocated</p>
                    <p className="font-medium">{currency(f.allocatedAmount || 0)}</p>
                  </div>
                  <div className="bg-muted/30 p-2 rounded">
                    <p className="text-muted-foreground mb-1">Utilized</p>
                    <p className="font-medium">{currency(f.utilizedAmount || 0)}</p>
                  </div>
                  <div className="bg-muted/30 p-2 rounded border border-primary/20 bg-primary/5">
                    <p className="text-primary/80 mb-1">Available</p>
                    <p className="font-medium text-primary">{currency((f.allocatedAmount || 0) - (f.utilizedAmount || 0))}</p>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      </div>
    </>
  );
}
