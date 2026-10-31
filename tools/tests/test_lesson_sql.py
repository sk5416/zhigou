"""Exercise the actual SQL printed in lessons, with fictional disposable data only."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from pathlib import Path
import re
import sqlite3
import tempfile
import threading
import unittest

CONTENT = Path(__file__).resolve().parents[2] / 'content/chapters'

@contextmanager
def database(*args, **kwargs):
    # sqlite3's own context commits/rolls back, but does not close the file.
    connection = sqlite3.connect(*args, **kwargs)
    try:
        with connection:
            yield connection
    finally:
        connection.close()

def lesson_sql(cid):
    body = (CONTENT / f'{cid}.md').read_text(encoding='utf-8')
    blocks = re.findall(r'^```sql\n(.*?)^```', body, re.M | re.S)
    if len(blocks) != 1:
        raise AssertionError(f'{cid}: expected exactly one teaching SQL block')
    return blocks[0]

class LessonSqlTests(unittest.TestCase):
    def test_c21_identity_uniqueness_and_orphan_rejection(self):
        with database(':memory:') as db:
            db.executescript(lesson_sql('C21'))
            self.assertEqual(db.execute('PRAGMA foreign_keys').fetchone(), (1,))
            db.execute("INSERT INTO project VALUES (1,'ERP','O1','1001')")
            db.execute("INSERT INTO project VALUES (2,'CRM','O1','1001')")
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute("INSERT INTO project VALUES (3,'ERP','O1','1001')")
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('INSERT INTO expense VALUES (1,99,100)')
            db.execute('INSERT INTO expense VALUES (1,1,100)')
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('DELETE FROM project WHERE id=1')

    def test_c22_money_status_and_deleted_key_rules(self):
        with database(':memory:') as db:
            db.executescript(lesson_sql('C22'))
            sql = 'INSERT INTO payment(request_no,amount_cents,currency,status,created_by,created_at) VALUES (?,?,?,?,?,?)'
            base = ['P1',0,'CNY','DRAFT','U1','2026-10-31T00:00:00Z']
            db.execute(sql, base)
            for number, value in [(1,-1),(1,None),(1,1.5),(2,'USD'),(3,'UNKNOWN'),(4,None)]:
                args = list(base)
                args[0] = 'INVALID'
                args[number] = value
                with self.subTest(field=number,value=value), self.assertRaises(sqlite3.IntegrityError):
                    db.execute(sql,args)
            db.execute("UPDATE payment SET deleted_at='2026-10-31T01:00:00Z' WHERE request_no='P1'")
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute(sql,base)

    def test_c23_plan_and_query_preserve_stable_results(self):
        with database(':memory:') as db:
            db.execute('CREATE TABLE ledger(id INTEGER PRIMARY KEY,org TEXT,posted_at TEXT,cents INTEGER)')
            db.executemany('INSERT INTO ledger VALUES (?,?,?,?)', [(i,'O1' if i%2 else 'O2',f'2026-10-{i%28+1:02d}T00:00:00Z',i*10) for i in range(1,301)])
            block = lesson_sql('C23')
            index_sql, plan_sql = block.split(';',1)
            query = plan_sql.split('EXPLAIN QUERY PLAN',1)[1]
            before = db.execute(query).fetchall()
            db.execute(index_sql)
            plan = db.execute(plan_sql).fetchall()
            self.assertTrue(plan)
            self.assertEqual(db.execute(query).fetchall(),before)
            self.assertEqual(len(before),50)
            self.assertEqual(before,sorted(before,key=lambda row:(row[1],row[0]),reverse=True))

    def test_c24_two_readers_stale_version_and_rollback(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'stock.db'
            with database(path) as db:
                db.execute('CREATE TABLE stock(sku TEXT PRIMARY KEY,qty INTEGER CHECK(qty>=0),version INTEGER)')
                db.execute("INSERT INTO stock VALUES ('M1',5,1)")
            barrier, committed = threading.Barrier(2), threading.Event()
            sql = lesson_sql('C24')
            update = sql.split(';',1)[1].strip()
            def worker(first):
                with database(path, timeout=3, isolation_level=None) as db:
                    self.assertEqual(db.execute("SELECT qty,version FROM stock WHERE sku='M1'").fetchone(),(5,1))
                    barrier.wait(timeout=5) # both read before either writes
                    if not first and not committed.wait(timeout=5):
                        raise AssertionError('first writer did not commit')
                    db.execute('BEGIN')
                    count = db.execute(update).rowcount
                    db.execute('COMMIT' if count == 1 else 'ROLLBACK')
                    if first: committed.set()
                    return count
            with ThreadPoolExecutor(max_workers=2) as pool:
                first, second = pool.submit(worker,True), pool.submit(worker,False)
                self.assertEqual((first.result(timeout=10),second.result(timeout=10)),(1,0))
            with database(path, isolation_level=None) as db:
                self.assertEqual(db.execute('SELECT qty,version FROM stock').fetchone(),(1,2))
                db.execute('UPDATE stock SET qty=5,version=1')
                db.executescript(sql)
                self.assertEqual(db.execute('SELECT qty,version FROM stock').fetchone(),(1,2))
                db.execute('ROLLBACK')
                self.assertEqual(db.execute('SELECT qty,version FROM stock').fetchone(),(5,1))

    def test_c26_backup_restore_and_damaged_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            backup = Path(directory)/'snapshot.db'
            with database(':memory:') as source, database(backup) as saved:
                source.executescript(lesson_sql('C21'))
                source.execute("INSERT INTO project VALUES (1,'ERP','O1','0012')")
                source.execute('INSERT INTO expense VALUES (1,1,12345)')
                source.commit()
                source.backup(saved)
            with database(backup) as saved, database(':memory:') as restored:
                saved.backup(restored)
                self.assertEqual(restored.execute('PRAGMA integrity_check').fetchone(),('ok',))
                self.assertEqual(restored.execute('PRAGMA foreign_key_check').fetchall(),[])
                self.assertEqual(restored.execute('SELECT p.code,e.cents FROM project p JOIN expense e ON p.id=e.project_id').fetchall(),[('0012',12345)])
            damaged = Path(directory)/'damaged.db'
            damaged.write_bytes(backup.read_bytes()[:64])
            with database(damaged) as db, self.assertRaises(sqlite3.DatabaseError):
                db.execute('SELECT * FROM project').fetchall()

if __name__ == '__main__':
    unittest.main()
