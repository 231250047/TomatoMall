#!/usr/bin/env python3
"""Insert-only synthetic fixtures into an explicitly selected isolated MySQL database."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent

def digest(product):
    return hashlib.sha256(json.dumps(product,sort_keys=True,ensure_ascii=False).encode()).hexdigest()

def literal(value):
    # Hex literals avoid dependence on NO_BACKSLASH_ESCAPES and client quoting.
    return 'CONVERT(0x'+str(value).encode('utf-8').hex()+' USING utf8mb4)'

def sql_for(products, seller_id):
    lines=['''CREATE TABLE IF NOT EXISTS rag_fixture_products (
fixture_key VARCHAR(64) PRIMARY KEY, product_id INT NOT NULL UNIQUE,
fixture_sha256 CHAR(64) NOT NULL,
CONSTRAINT fk_rag_fixture_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;''', 'START TRANSACTION;']
    for p in products:
        key=literal(p['fixture_key'])
        lines.append(f'SET @fixture_pid=(SELECT product_id FROM rag_fixture_products WHERE fixture_key={key});')
        fields=['title','description','detail','tag','price','rate','status','product_condition']
        values=[literal(p[f]) for f in fields]
        values += ['NOW()',str(seller_id) if seller_id is not None else 'NULL', literal('/favicon.ico')]
        lines.append(f"INSERT INTO products ({','.join(fields)},create_time,seller_id,cover) SELECT {','.join(values)} WHERE @fixture_pid IS NULL;")
        lines.append('SET @fixture_new=ROW_COUNT();')
        lines.append('SET @fixture_pid=IF(@fixture_new=1,LAST_INSERT_ID(),@fixture_pid);')
        lines.append(f"INSERT INTO stockpiles(product_id,amount,frozen) SELECT @fixture_pid,{p['amount']},{p['frozen']} WHERE @fixture_new=1;")
        for item,value in p['specifications'].items():
            lines.append(f'INSERT INTO specifications(product_id,item,value) SELECT @fixture_pid,{literal(item)},{literal(value)} WHERE @fixture_new=1;')
        lines.append(f"INSERT INTO rag_fixture_products(fixture_key,product_id,fixture_sha256) SELECT {key},@fixture_pid,{literal(digest(p))} WHERE @fixture_new=1;")
    lines.append('COMMIT;')
    return '\n'.join(lines)+'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',required=True,help='Must end in _rag_eval; initialize application schema first')
    parser.add_argument('--mysql',default='mysql',help='mysql CLI executable path')
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=3306)
    parser.add_argument('--user',default='root')
    parser.add_argument('--defaults-extra-file',help='Private MySQL option file; never commit credentials')
    parser.add_argument('--seller-id',type=int,help='Optional existing seller ID in this isolated database')
    parser.add_argument('--mapping-out',type=Path,help='Write fixture key -> actual database product ID JSON')
    parser.add_argument('--sql-only',action='store_true',help='Print SQL without connecting; keep the same safety rules when executing manually')
    args=parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+_rag_eval',args.database):
        parser.error('Refusing non-isolated database: name must match [A-Za-z0-9_]+_rag_eval')
    products=json.loads((ROOT/'products.json').read_text())
    if args.sql_only:
        print('-- Execute ONLY against the empty/fixture-only database '+args.database)
        print(sql_for(products,args.seller_id),end='')
        return
    command=[args.mysql]
    if args.defaults_extra_file: command.append('--defaults-extra-file='+str(Path(args.defaults_extra_file).resolve()))
    command += ['--protocol=TCP','--host='+args.host,'--port='+str(args.port),'--user='+args.user,
                '--database='+args.database,'--default-character-set=utf8mb4','--batch','--skip-column-names']
    def execute(sql):
        result=subprocess.run(command,input=sql,text=True,capture_output=True)
        if result.returncode:
            # mysql stderr can echo the SQL but contains only synthetic fixture data.
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()
    # Never initialize or alter business schema implicitly.
    execute('SELECT id,title,price,rate,description,detail,tag,create_time,seller_id,status,product_condition FROM products LIMIT 0; SELECT product_id,amount,frozen FROM stockpiles LIMIT 0; SELECT product_id,item,value FROM specifications LIMIT 0;')
    has_mapping=int(execute("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='rag_fixture_products';"))
    if has_mapping:
        foreign_count=int(execute('SELECT COUNT(*) FROM products p LEFT JOIN rag_fixture_products f ON p.id=f.product_id WHERE f.product_id IS NULL;'))
        known={line.split('\t')[0]:line.split('\t')[1] for line in execute('SELECT fixture_key,fixture_sha256 FROM rag_fixture_products;').splitlines()}
        if set(known)-{p['fixture_key'] for p in products}:
            raise RuntimeError('Unknown fixture generation in this database; use a fresh evaluation database')
        for p in products:
            if p['fixture_key'] in known and known[p['fixture_key']] != digest(p):
                raise RuntimeError('Fixture changed: '+p['fixture_key']+'; create a new evaluation database instead of overwriting')
    else:
        foreign_count=int(execute('SELECT COUNT(*) FROM products;'))
    if foreign_count:
        raise RuntimeError('Refusing database containing non-fixture products; use a fresh isolated evaluation database')
    execute(sql_for(products,args.seller_id))
    mapping={key:int(pid) for key,pid in (line.split('\t') for line in execute('SELECT fixture_key,product_id FROM rag_fixture_products ORDER BY fixture_key;').splitlines())}
    if len(mapping)!=len(products): raise RuntimeError('Unexpected mapping count after import')
    if args.mapping_out:
        args.mapping_out.write_text(json.dumps(mapping,ensure_ascii=False,indent=2)+'\n')
    print(f'Imported/retained {len(mapping)} fixtures in {args.database}; existing product and stock rows were not modified.')

if __name__=='__main__':
    try: main()
    except (RuntimeError,OSError) as exc:
        print(str(exc),file=sys.stderr)
        sys.exit(1)
