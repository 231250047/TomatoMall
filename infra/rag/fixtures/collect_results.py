#!/usr/bin/env python3
"""Collect authenticated search responses; never hide degraded or unknown-ID results."""
import argparse
import json
import os
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent

def collect(query, response, ids):
    return {
        'query_id': query['query_id'],
        'fixture_keys': [ids.get(int(item['product']['id']), 'unknown:' + str(item['product']['id'])) for item in response['items']],
        'degraded': response['degraded'],
        'strategy': response['strategy'],
        'elapsed_ms': response['elapsedMs'],
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18080')
    parser.add_argument('--queries',type=Path,default=ROOT/'queries.json')
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--split', choices=['dev', 'heldout', 'all'], default='dev')
    parser.add_argument('--mode', choices=['keyword', 'hybrid', 'vector'], default='keyword')
    args = parser.parse_args()
    token = os.environ.get('RAG_EVAL_TOKEN')
    if not token:
        parser.error('Set RAG_EVAL_TOKEN locally; never commit the token')
    mapping = json.loads(args.mapping.read_text())
    ids = {int(value): key for key, value in mapping.items()}
    if len(ids) != 100:
        parser.error('Expected the 100-product fixture mapping')
    queries = json.loads(args.queries.read_text(encoding='utf-8'))
    rows, raw = [], []
    for q in queries:
        if args.split != 'all' and q['split'] != args.split:
            continue
        c = q['constraints']
        body = {'query': q['query'], 'topK': 5, 'mode': args.mode, 'inStockOnly': c.get('in_stock', True)}
        if 'category' in c: body['tag'] = c['category']
        if 'max_price' in c: body['maxPrice'] = c['max_price']
        for field in ('level','excludedTopics'):
            if field in c: body[field]=c[field]
        request = urllib.request.Request(args.base_url.rstrip('/') + '/api/test/vectorstore/search',
            json.dumps(body).encode(), {'Content-Type': 'application/json', 'token': token})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.load(response)
        rows.append(collect(q, data, ids))
        raw.append({'query_id': q['query_id'], 'response': data})
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n')
    args.output.with_suffix('.raw.json').write_text(json.dumps(raw, ensure_ascii=False, indent=2) + '\n')
    print(f'Collected {len(rows)} queries; degraded={sum(r["degraded"] for r in rows)}. Raw responses saved alongside results.')

if __name__ == '__main__':
    main()
