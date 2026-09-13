/** Downloads a small CSV report generated from the live client-side data. */
export function downloadCsv(filename: string, headers: string[], rows: Array<Array<string | number | undefined | null>>): void {
  const escape = (value: string | number | undefined | null) => {
    const text = value == null ? '' : String(value);
    return `"${text.replace(/"/g, '""')}"`;
  };
  const csv = [headers, ...rows].map((row) => row.map(escape).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
