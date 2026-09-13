export interface Donation {
  id?: string;
  donationId: string;
  donorType: 'INDIVIDUAL' | 'ORGANIZATION' | 'CORPORATE' | 'ANONYMOUS';
  donationType: 'CASH' | 'GOODS' | 'SERVICES' | 'ONLINE';
  donorName?: string;
  amount?: number;
  itemName?: string;
  quantity?: number;
  purpose?: string;
  operationId?: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'ALLOCATED' | 'COMPLETED';
  createdAt: number;
  verifiedAt?: number;
  verifiedBy?: string;
  notes?: string;
  // -- Online Transaction Fields --
  timestamp?: number;
  orderId?: string;
  paymentId?: string;
  userId?: string;
  date?: string;
}

export interface Fund {
  id?: string;
  fundId: string;
  source: string;
  totalAmount: number;
  requiredAmount?: number;
  allocatedAmount: number;
  utilizedAmount: number;
  remainingAmount: number;
  purpose: string;
  operationId?: string;
  status: 'ACTIVE' | 'CLOSED' | 'DEPLETED';
  createdAt: number;
  createdBy: string;
}

export interface Allocation {
  id?: string;
  allocationId: string;
  fundId: string;
  operationId?: string;
  category: 'MEDICAL' | 'FOOD' | 'WATER' | 'SHELTER' | 'TRANSPORTATION' | 'RESCUE' | 'EQUIPMENT' | 'OTHER';
  amount: number;
  utilizedAmount?: number;
  remainingAmount?: number;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'UTILIZED';
  requestedBy: string;
  approvedBy?: string;
  createdAt: number;
  approvedAt?: number;
  paidAt?: number;
  paidBy?: string;
}

export interface Expense {
  id?: string;
  expenseId: string;
  fundId?: string;
  allocationId?: string;
  amount: number;
  category: 'MEDICAL' | 'FOOD' | 'WATER' | 'SHELTER' | 'TRANSPORTATION' | 'RESCUE' | 'EQUIPMENT' | 'OTHER';
  operationId?: string;
  incidentId?: string;
  vendor?: string;
  description: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAID';
  createdAt: number;
  createdBy: string;
  approvedBy?: string;
  approvedAt?: number;
  paidAt?: number;
  paidBy?: string;
  receiptUrl?: string;
  notes?: string;
}

export interface FinancialReport {
  id?: string;
  reportId: string;
  name: string;
  period: string;
  status: 'GENERATED';
  generatedAt: number;
  generatedBy: string;
  snapshot: {
    income: number;
    allocated: number;
    expenditure: number;
    netPosition: number;
    fundCount: number;
    fundBalances: Array<{
      fundId: string;
      purpose: string;
      available: number;
    }>;
  };
}
