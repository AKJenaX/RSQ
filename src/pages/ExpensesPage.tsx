import React, { useMemo, useState, useEffect } from 'react';
import { useSearchParams } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Plus, Search } from "lucide-react";
import { PageHeader, Panel, StatCard, StatusBadge } from "../components/ui-kit";
import { useFinance } from "../hooks/useFinance";
import { useAuth } from '../hooks/useAuth';
import { ExpenseModal } from '../components/Modals/ExpenseModal';
import { formatTimestamp } from '../utils/formatters';
import { approveExpense, rejectExpense, payExpense } from '../services/financeService';

const currency = (n: number) => n.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

const tooltipStyle = {
  backgroundColor: "var(--color-card)",
  border: "1px solid var(--color-border)",
  borderRadius: "8px",
  fontSize: "12px",
};

const mapExpenseStatus = (s: string) => {
  if (s === "Processing") return "PAID";
  return s.toUpperCase();
};

export function ExpensesPage() {
  const { expenses, funds, error } = useFinance();
  const paidExpenses = expenses.filter(e => e.status === "PAID");
  const totalExpenses = paidExpenses.reduce((acc, e) => acc + (e.amount || 0), 0);
  const pendingExpenses = expenses.filter(e => e.status === "PENDING").reduce((acc, e) => acc + (e.amount || 0), 0);
  const thisMonthExpenses = totalExpenses;
  const expenseCategories = Array.from(new Set(paidExpenses.map(e => e.category)));
  const expenseChart = expenseCategories.map(cat => ({
    name: cat,
    amount: paidExpenses.filter(e => e.category === cat).reduce((acc, e) => acc + (e.amount || 0), 0)
  }));
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("All");
  const [showExpenseModal, setShowExpenseModal] = useState(false);
  const [expandedExpenseId, setExpandedExpenseId] = useState<string | null>(null);
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const expenseId = searchParams.get('expenseId');

  useEffect(() => {
    if (expenseId) {
      setExpandedExpenseId(expenseId);
      setTimeout(() => {
        const el = document.getElementById(`expense-${expenseId}`);
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 100);
    }
  }, [expenseId]);
  
  const handleApprove = async (id: string) => {
    if (!user) return;
    try { await approveExpense(id, user.uid); } catch (err) { console.error(err); }
  };
  const handleReject = async (id: string) => {
    if (!user) return;
    try { await rejectExpense(id, user.uid); } catch (err) { console.error(err); }
  };
  const handlePay = async (id: string) => {
    if (!user) return;
    try { await payExpense(id, user.uid); } catch (err) { console.error(err); }
  };

  const rows = useMemo(
    () =>
      expenses.filter(
        (e) =>
          (status === "All" || e.status === mapExpenseStatus(status)) &&
          (e.vendor + e.category + e.id).toLowerCase().includes(query.toLowerCase()),
      ),
    [query, status, expenses],
  );
  const total = rows.reduce((s, e) => s + e.amount, 0);

  return (
    <>
      <PageHeader
        title="Expenses"
        subtitle="Operational disbursements and vendor payments"
        actions={
          <button onClick={() => setShowExpenseModal(true)} className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">
            <Plus className="h-4 w-4" /> Record expense
          </button>
        }
      />

      {error && <p className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">Expense records could not be loaded from Firestore.</p>}
      {!error && expenses.length === 0 && <p className="rounded-md border border-border bg-muted/20 px-3 py-2 text-sm text-muted-foreground">No expenses have been recorded yet.</p>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Spend (Sep)" value={currency(thisMonthExpenses)} hint="vs August" />
        <StatCard label="Awaiting Approval" value={currency(pendingExpenses)} hint={`${expenses.filter(e => e.status === "PENDING").length} request${expenses.filter(e => e.status === "PENDING").length === 1 ? '' : 's'}`} />
        <StatCard label="Vendors Paid" value={Array.from(new Set(paidExpenses.map(e => e.vendor))).length.toString()} hint="this month" />
        <StatCard label="Rejected" value={expenses.filter(e => e.status === "REJECTED").length.toString()} hint="policy breach" />
      </div>

      <Panel title="Spend by Category" description="Current month">
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={expenseChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
              <XAxis dataKey="name" stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} />
              <YAxis stroke="var(--color-muted-foreground)" fontSize={12} tickLine={false} axisLine={false} width={60} tickFormatter={(v) => currency(v as number)} />
              <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "var(--color-accent)" }} formatter={(v: any) => currency(v as number)} />
              <Bar dataKey="amount" fill="var(--color-chart-4)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Panel>

      <Panel title="Expense Register" description={`${rows.length} records · ${currency(total)} total`}>
        <div className="mb-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search vendor, category or reference"
              className="w-full rounded-md border border-input bg-background py-2 pl-9 pr-3 text-sm outline-none focus:border-ring"
            />
          </div>
          <select
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            className="rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:border-ring"
          >
            {["All", "Approved", "Pending", "Processing", "Rejected"].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th className="pb-2 font-medium">Reference</th>
                <th className="pb-2 font-medium">Category</th>
                <th className="pb-2 font-medium">Vendor</th>
                <th className="pb-2 font-medium">Recorded</th>
                <th className="pb-2 font-medium">Status</th>
                <th className="pb-2 text-right font-medium">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {rows.map((e) => {
                const fund = e.fundId ? funds.find(f => f.fundId === e.fundId || f.id === e.fundId) : null;
                return (
                  <React.Fragment key={e.id}>
                    <tr 
                      id={`expense-${e.id}`}
                      className={`cursor-pointer hover:bg-accent/40 transition-colors ${expandedExpenseId === e.id ? 'bg-primary/5 ring-1 ring-inset ring-primary' : ''}`}
                      onClick={() => setExpandedExpenseId(expandedExpenseId === e.id ? null : (e.id || null))}
                    >
                      <td className="py-3 font-mono text-xs text-muted-foreground">{e.id}</td>
                      <td className="py-3 font-medium">{e.category}</td>
                      <td className="py-3 text-muted-foreground">{e.vendor}</td>
                      <td className="py-3 text-muted-foreground">{formatTimestamp(e.createdAt)}</td>
                      <td className="py-3"><StatusBadge label={e.status} /></td>
                      <td className="py-3 text-right tabular-nums">{currency(e.amount)}</td>
                    </tr>
                    {expandedExpenseId === e.id && (
                      <tr className="bg-muted/20">
                        <td colSpan={6} className="px-4 py-4">
                          <div className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-4 lg:grid-cols-5">
                            <div>
                              <p className="font-semibold text-foreground">Reference</p>
                              <p className="font-mono text-xs text-muted-foreground">{e.id}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Category</p>
                              <p className="text-muted-foreground">{e.category}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Amount</p>
                              <p className="text-muted-foreground tabular-nums">{currency(e.amount)}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Vendor</p>
                              <p className="text-muted-foreground">{e.vendor || '—'}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Description</p>
                              <p className="text-muted-foreground">{e.description || '—'}</p>
                            </div>
                            
                            <div>
                              <p className="font-semibold text-foreground">Fund Name</p>
                              <p className="text-muted-foreground">{fund ? (fund.purpose || fund.fundId) : '—'}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Fund ID</p>
                              <p className="font-mono text-xs text-muted-foreground">{fund?.fundId || fund?.id || '—'}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Created Date</p>
                              <p className="text-muted-foreground">{formatTimestamp(e.createdAt)}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Created By</p>
                              <p className="text-muted-foreground font-mono text-xs">{e.createdBy || '—'}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Status</p>
                              <p className="text-muted-foreground">{e.status}</p>
                            </div>
                            
                            <div>
                              <p className="font-semibold text-foreground">Approved By</p>
                              <p className="text-muted-foreground font-mono text-xs">{e.approvedBy || '—'}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Approved Date</p>
                              <p className="text-muted-foreground">{formatTimestamp(e.approvedAt)}</p>
                            </div>
                            <div>
                              <p className="font-semibold text-foreground">Paid Date</p>
                              <p className="text-muted-foreground">{formatTimestamp(e.paidAt)}</p>
                            </div>
                          </div>
                          <div className="mt-4 flex items-center justify-end gap-2 border-t border-border pt-4">
                            {e.status === 'PENDING' && (
                              <>
                                <button
                                  type="button"
                                  onClick={(ev) => { ev.stopPropagation(); handleReject(e.id!); }}
                                  className="rounded-md border border-border bg-background px-3 py-1.5 text-xs font-medium text-foreground hover:bg-secondary"
                                >
                                  Reject
                                </button>
                                <button
                                  type="button"
                                  onClick={(ev) => { ev.stopPropagation(); handleApprove(e.id!); }}
                                  className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:opacity-90"
                                >
                                  Approve
                                </button>
                              </>
                            )}
                            {e.status === 'APPROVED' && (
                              <button
                                type="button"
                                onClick={(ev) => { ev.stopPropagation(); handlePay(e.id!); }}
                                className="rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700"
                              >
                                Pay Expense
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </Panel>
      {showExpenseModal && user ? <ExpenseModal authorityUid={user.uid} funds={funds} onClose={() => setShowExpenseModal(false)} /> : null}
    </>
  );
}
