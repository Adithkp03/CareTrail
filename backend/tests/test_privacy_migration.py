from pathlib import Path
from unittest.mock import Mock
import pytest
from app.migrate import secure_public_tables


def test_sqlite_no_security_sql():
    conn=Mock(); conn.dialect.name='sqlite'
    secure_public_tables(conn)
    conn.execute.assert_not_called()

def test_plain_postgres_without_supabase_roles():
    conn=Mock();conn.dialect.name='postgresql'
    conn.execute.return_value.scalars.return_value.all.return_value=[]
    secure_public_tables(conn)
    assert conn.execute.call_count==1

def test_supabase_privacy_sql_covers_all_tables_and_future_grants():
    conn=Mock();conn.dialect.name='postgresql'
    conn.execute.return_value.scalars.return_value.all.return_value=['anon','authenticated']
    secure_public_tables(conn)
    sql=str(conn.execute.call_args.args[0])
    assert "SELECT tablename FROM pg_tables WHERE schemaname = 'public'" in sql
    assert 'ENABLE ROW LEVEL SECURITY' in sql
    assert 'REVOKE ALL PRIVILEGES ON TABLE' in sql
    assert 'REVOKE ALL PRIVILEGES ON ALL SEQUENCES' in sql
    assert 'ALTER DEFAULT PRIVILEGES FOR ROLE' in sql
    assert 'current_user' in sql
    assert 'FROM anon, authenticated' in sql
    assert 'FORCE ROW LEVEL SECURITY' not in sql
    assert 'FROM postgres' not in sql
    assert 'CREATE POLICY' not in sql

def test_partial_supabase_roles_fail_closed():
    conn=Mock();conn.dialect.name='postgresql'
    conn.execute.return_value.scalars.return_value.all.return_value=['anon']
    with pytest.raises(RuntimeError):secure_public_tables(conn)
    assert conn.execute.call_count==1
