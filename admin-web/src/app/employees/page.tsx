"use client";

import AdminShell from "../../components/AdminShell";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { supabase } from "../../lib/supabase";

type Employee = {
  id: string;
  name: string;
  employee_code: string | null;
  guest_code: string | null;
  department: string | null;
  phone: string | null;
  active: boolean;
  id_type: "EMPLOYEE" | "GUEST";
};

export default function EmployeesPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<"ALL" | "EMPLOYEE" | "GUEST">("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "INACTIVE">("ACTIVE");
  const [departmentFilter, setDepartmentFilter] = useState("ALL");

  const load = () => {
    setLoading(true);
    setError("");
    supabase
      .from("employees")
      .select("id,name,employee_code,guest_code,department,phone,active,id_type")
      .order("name")
      .then(({ data, error: x }) => {
        if (x) setError(x.message);
        setEmployees((data ?? []) as Employee[]);
        setLoading(false);
      });
  };

  useEffect(load, []);

  async function toggle(employee: Employee) {
    setError("");
    const { error: x } = await supabase
      .from("employees")
      .update({ active: !employee.active })
      .eq("id", employee.id);

    if (x) setError(x.message);
    else load();
  }

  const departments = useMemo(
    () =>
      Array.from(
        new Set(
          employees
            .map((employee) => employee.department?.trim())
            .filter((department): department is string => Boolean(department))
        )
      ).sort((a, b) => a.localeCompare(b)),
    [employees]
  );

  const filteredEmployees = useMemo(() => {
    const term = search.trim().toLowerCase();

    return employees.filter((employee) => {
      const identifier = employee.employee_code || employee.guest_code || "";
      const matchesSearch =
        !term ||
        employee.name.toLowerCase().includes(term) ||
        identifier.toLowerCase().includes(term) ||
        (employee.department || "").toLowerCase().includes(term) ||
        (employee.phone || "").toLowerCase().includes(term);

      const matchesType = typeFilter === "ALL" || employee.id_type === typeFilter;
      const matchesStatus =
        statusFilter === "ALL" ||
        (statusFilter === "ACTIVE" ? employee.active : !employee.active);
      const matchesDepartment =
        departmentFilter === "ALL" || employee.department === departmentFilter;

      return matchesSearch && matchesType && matchesStatus && matchesDepartment;
    });
  }, [employees, search, typeFilter, statusFilter, departmentFilter]);

  const activeCount = employees.filter((employee) => employee.active).length;
  const guestCount = employees.filter((employee) => employee.id_type === "GUEST").length;

  function resetFilters() {
    setSearch("");
    setTypeFilter("ALL");
    setStatusFilter("ACTIVE");
    setDepartmentFilter("ALL");
  }

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>Employees</h1>
            <p>Manage permanent employee and guest identifiers.</p>
          </div>
          <Link className="primary-button small-button" href="/employees/new">
            + Add employee
          </Link>
        </header>

        {error && <p className="error-text">{error}</p>}

        <section className="stats-grid">
          <div className="stat-card"><span>Total records</span><strong>{employees.length}</strong></div>
          <div className="stat-card"><span>Active</span><strong>{activeCount}</strong></div>
          <div className="stat-card"><span>Guests</span><strong>{guestCount}</strong></div>
          <div className="stat-card"><span>Showing</span><strong>{filteredEmployees.length}</strong></div>
        </section>

        <section className="panel filters-panel">
          <div className="filters-grid">
            <div className="field">
              <label>Search</label>
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Name, ID, department or phone"
              />
            </div>
            <div className="field">
              <label>Type</label>
              <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as typeof typeFilter)}>
                <option value="ALL">All types</option>
                <option value="EMPLOYEE">Employees</option>
                <option value="GUEST">Guests</option>
              </select>
            </div>
            <div className="field">
              <label>Status</label>
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}>
                <option value="ACTIVE">Active</option>
                <option value="ALL">All statuses</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </div>
            <div className="field">
              <label>Department</label>
              <select value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)}>
                <option value="ALL">All departments</option>
                {departments.map((department) => (
                  <option key={department} value={department}>{department}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button className="secondary-button" onClick={resetFilters}>Reset filters</button>
          </div>
        </section>

        <section className="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>Name</th><th>ID</th><th>Type</th><th>Department</th><th>Phone</th><th>Status</th><th>Action</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={7}>Loading...</td></tr>
              ) : filteredEmployees.length === 0 ? (
                <tr><td colSpan={7}>No employees match the current filters.</td></tr>
              ) : (
                filteredEmployees.map((employee) => (
                  <tr key={employee.id}>
                    <td><strong>{employee.name}</strong></td>
                    <td>{employee.employee_code || employee.guest_code}</td>
                    <td>{employee.id_type === "GUEST" ? "Guest" : "Employee"}</td>
                    <td>{employee.department || "—"}</td>
                    <td>{employee.phone || "—"}</td>
                    <td>
                      <span className={`status ${employee.active ? "status-active" : "status-inactive"}`}>
                        {employee.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td>
                      <Link className="secondary-button" href={`/employees/${employee.id}/edit`}>Edit</Link>{" "}
                      <button className="secondary-button" onClick={() => toggle(employee)}>
                        {employee.active ? "Deactivate" : "Activate"}
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      </div>
    </AdminShell>
  );
}
