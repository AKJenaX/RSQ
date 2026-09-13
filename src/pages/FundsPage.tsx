import React, { useState } from "react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { Plus } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { useFinance } from "../hooks/useFinance";
import { formatTimestamp } from "../utils/formatters";
import { FundModal } from "../components/Modals/FundModal";
import { AllocationModal } from "../components/Modals/AllocationModal";

const currency = (n: number) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

export function FundsPage() {
  const { funds, allocations, loadState } = useFinance();
  const [showFundModal, setShowFundModal] = useState(false);
  const [showAllocationModal, setShowAllocationModal] = useState(false);
  const [expandedAllocationId, setExpandedAllocationId] = useState<string | null>(null);

  const totalBudget = funds.reduce((acc, f) => acc + (f.totalAmount || 0), 0);
  const totalAllocated = funds.reduce((s, f) => s + (f.allocatedAmount || 0), 0);
  const totalUtilized = funds.reduce((s, f) => s + (f.utilizedAmount || 0), 0);
  const unallocated = totalBudget - totalAllocated;

  return (
    <>
      <PageHeader
        title="Funds & Allocation"
        subtitle="Programme budgets and remaining balances"
        actions={
          <div className="flex gap-2">
            <button
              onClick={() => setShowAllocationModal(true)}
              className="inline-flex items-center gap-2 rounded-md border border-border bg-background px-3 py-2 text-sm font-medium text-foreground hover:bg-secondary"
            >
              <Plus className="h-4 w-4" /> Create Allocation
            </button>
            <button
              onClick={() => setShowFundModal(true)}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
            >
              <Plus className="h-4 w-4" /> Create Fund
            </button>
          </div>
        }
      />
      {loadState === 'permission-denied' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          You do not have permission to view funds and allocations.
        </p>
      )}
      {loadState === 'error' && (
        <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
          Fund and allocation records could not be loaded. Please check your connection.
        </p>
      )}
      {(loadState === 'success' || loadState === 'empty') && funds.length === 0 && <p className="rounded-md border border-border bg-muted/20 px-3 py-2 text-sm text-muted-foreground">No funds have been created yet.</p>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Total Budget" value={currency(totalBudget)} hint={`across ${funds.length} programme${funds.length === 1 ? '' : 's'}`} />
        <StatCard label="Total Allocated" value={currency(totalAllocated)} hint="budget distributed" />
        <StatCard label="Total Utilized" value={currency(totalUtilized)} hint="spend to date" />
        <StatCard label="Unallocated" value={currency(unallocated)} hint="unallocated balance" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Allocation Split" description="Share of total budget">
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={funds}
                  dataKey="allocatedAmount"
                  nameKey="purpose"
                  innerRadius={50}
                  outerRadius={82}
                  paddingAngle={2}
                  stroke="var(--color-card)"
                >
                  {funds.map((_, i: number) => (
                    <Cell key={i} fill={`var(--color-chart-${(i % 5) + 1})`} />
                  ))}
                </Pie>
                <Tooltip contentStyle={tooltipStyle} formatter={(v: any) => currency(v as number)} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Panel>

        <Panel title="Programme Budgets" description="Spend against allocation" className="lg:col-span-2">
          <ul className="space-y-4">
            {funds.map((f, i) => {
              const allocatedPct = Math.round(((f.allocatedAmount || 0) / (f.totalAmount || 1)) * 100);
              const utilizedPct = Math.round(((f.utilizedAmount || 0) / (f.allocatedAmount || 1)) * 100);
              return (
                <li key={f.fundId} className="rounded-md border border-border p-4">
                  <div className="mb-2 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold">{f.purpose || f.fundId}</p>
                      <p className="text-xs text-muted-foreground">
                        Created: {formatTimestamp(f.createdAt)} | Budget: {currency(f.totalAmount)} | Available Allocated: {currency(f.remainingAmount)}
                      </p>
                    </div>
                    <StatusBadge
                      label={`${allocatedPct}% allocated`}
                      tone={allocatedPct > 90 ? "critical" : allocatedPct > 70 ? "high" : "success"}
                    />
                  </div>
                  
                  <div className="mt-4 flex items-center justify-between text-xs text-muted-foreground">
                    <span>Allocated: {currency(f.allocatedAmount)}</span>
                    <span>Utilized: {currency(f.utilizedAmount)} ({f.allocatedAmount > 0 ? utilizedPct : 0}%)</span>
                  </div>
                  <div className="mt-1 h-2 overflow-hidden rounded-full bg-secondary">
                    <div className="h-full rounded-full transition-all" style={{ width: `${f.allocatedAmount > 0 ? utilizedPct : 0}%`, backgroundColor: `var(--color-chart-${(i % 5) + 1})` }} />
                  </div>
                </li>
              );
            })}
            {funds.length === 0 ? (
              <li className="py-6 text-center text-sm text-muted-foreground">No funds are available in Firestore.</li>
            ) : null}
          </ul>
        </Panel>
      </div>

        <Panel title="Recent Allocations" description="Live transfers from Firestore">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th className="pb-2 font-medium">Reference</th>
                <th className="pb-2 font-medium">From</th>
                <th className="pb-2 font-medium">To</th>
                <th className="pb-2 font-medium">Approved by</th>
                <th className="pb-2 font-medium">Date</th>
                <th className="pb-2 font-medium">Status</th>
                <th className="pb-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {allocations.map((allocation) => {
                const fund = funds.find((item) => item.id === allocation.fundId || item.fundId === allocation.fundId);
                const allocId = allocation.id ?? allocation.allocationId;
                return (
                  <React.Fragment key={allocId}>
                    <tr 
                      className="hover:bg-accent/40 cursor-pointer transition-colors"
                      onClick={() => setExpandedAllocationId(expandedAllocationId === allocId ? null : allocId)}
                    >
                      <td className="py-3 font-mono text-xs text-muted-foreground">{allocation.allocationId}</td>
                      <td className="py-3 text-muted-foreground">{fund?.source || 'Not specified'}</td>
                      <td className="py-3 font-medium">{fund?.purpose || allocation.fundId || 'Unlinked fund'}</td>
                      <td className="py-3 text-muted-foreground">{allocation.approvedBy || allocation.requestedBy || 'System'}</td>
                      <td className="py-3 text-muted-foreground">{formatTimestamp(allocation.approvedAt || allocation.createdAt)}</td>
                      <td className="py-3"><StatusBadge label={allocation.status} /></td>
                      <td className="py-3 text-right tabular-nums">{currency(allocation.amount)}</td>
                    </tr>
                    {expandedAllocationId === allocId && (
                      <tr className="bg-muted/20">
                        <td colSpan={7} className="px-4 py-4">
                          <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
                            <div>
                              <p className="font-semibold text-foreground">Allocation ID</p>
                              <p className="font-mono text-xs text-muted-foreground">{allocId}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Destination</p>
                              <p className="text-muted-foreground">{allocation.category}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Created Date</p>
                              <p className="text-muted-foreground">{formatTimestamp(allocation.createdAt)}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Reason</p>
                              <p className="text-muted-foreground">{allocation.reason || 'N/A'}</p>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
              {(loadState === 'success' || loadState === 'empty') && allocations.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-8 text-center text-sm text-muted-foreground">No allocations have been recorded.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </Panel>

      {showFundModal && <FundModal onClose={() => setShowFundModal(false)} />}
      {showAllocationModal && <AllocationModal funds={funds} onClose={() => setShowAllocationModal(false)} />}
    </>
  );
}
