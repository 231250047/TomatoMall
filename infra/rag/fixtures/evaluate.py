#!/usr/bin/env python3
"""Validate curated fixtures; optionally score externally collected retrieval results."""
import argparse
from collections import Counter
from decimal import Decimal
import json
import re
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def eligible(product,constraints):
    if product['status']!='available': return False
    if constraints.get('in_stock') and product['amount']-product['frozen']<=0: return False
    if 'category' in constraints and product['tag']!=constraints['category']: return False
    if 'max_price' in constraints and Decimal(product['price'])>Decimal(constraints['max_price']): return False
    if constraints.get('level') is not None and product['specifications'].get('适用难度')!=constraints['level']: return False
    text=' '.join([product['title'],product['description'],product['detail'],*product['specifications'].keys(),*product['specifications'].values()]).lower()
    for term in constraints.get('excludedTopics',[]):
        term=term.strip().lower()
        matched=bool(re.search(r'(?<![a-z0-9_])'+re.escape(term)+r'(?![a-z0-9_])',text)) if re.fullmatch(r'[a-z][a-z0-9+#.-]*',term) else term in text
        if matched:return False
    return True

def validate(products,queries):
    assert len(products)==100
    by_key={p['fixture_key']:p for p in products}
    assert len(by_key)==100
    assert Counter(p['tag'] for p in products)=={'science':48,'education':12,'literature':10,'art':6,'management':8,'history':6,'philosophy':5,'health':5}
    for p in products:
        for field,limit in [('title',255),('description',255),('detail',500)]: assert 0<len(p[field])<=limit
        assert Decimal(p['price'])>=0 and 0<=p['rate']<=10
        assert 0<=p['frozen']<=p['amount']
        assert p['status'] in {'available','unavailable'}
        for k,v in p['specifications'].items(): assert 0<len(k)<=50 and 0<len(v)<=255
    assert len(queries)==len({q['query_id'] for q in queries})==50
    assert Counter(q['split'] for q in queries)=={'dev':30,'heldout':20}
    for q in queries:
        relevant=set(q['relevant_fixture_keys']); forbidden=set(q['forbidden_fixture_keys'])
        assert not relevant & forbidden and (relevant|forbidden)<=by_key.keys()
        assert all(eligible(by_key[k],q['constraints']) for k in relevant),q['query_id']
        assert q['rationale'] and bool(relevant)==(q['expected_behavior']=='recommend')
    return by_key

def score(products,queries,predictions,k):
    expected={q['query_id']:q for q in queries}
    rows={}
    for row in predictions:
        qid=row['query_id']
        if qid not in expected: raise ValueError('Unexpected query '+qid)
        if qid in rows: raise ValueError('Duplicate query '+qid)
        keys=row['fixture_keys']
        if not isinstance(keys,list) or any(not isinstance(x,str) for x in keys): raise ValueError('fixture_keys must be a string array')
        if len(keys)!=len(set(keys)): raise ValueError('Duplicate result in '+qid)
        rows[qid]=row
    if rows.keys()!=expected.keys(): raise ValueError('Missing predictions: '+','.join(sorted(expected.keys()-rows.keys())))
    metrics={'queries':len(queries),'k':k,'labeled_precision_at_k':0.,'labeled_recall_at_k':0.,
             'hard_constraint_violating_queries':0,'forbidden_result_queries':0,'unknown_id_queries':0,
             'no_match_queries':0,'no_match_correct':0,'degraded_queries':0}
    recommendation_count=0
    for qid,q in expected.items():
        row=rows[qid]; keys=row['fixture_keys']; top=set(keys[:k]); relevant=set(q['relevant_fixture_keys'])
        metrics['unknown_id_queries']+=int(any(x not in products for x in keys))
        metrics['hard_constraint_violating_queries']+=int(any(x not in products or not eligible(products[x],q['constraints']) for x in keys))
        metrics['forbidden_result_queries']+=int(bool(set(keys)&set(q['forbidden_fixture_keys'])))
        metrics['degraded_queries']+=int(bool(row.get('degraded',False)))
        if relevant:
            recommendation_count+=1
            metrics['labeled_precision_at_k']+=len(top&relevant)/k
            metrics['labeled_recall_at_k']+=len(top&relevant)/len(relevant)
        else:
            metrics['no_match_queries']+=1
            metrics['no_match_correct']+=int(not keys)
    if recommendation_count:
        for m in ['labeled_precision_at_k','labeled_recall_at_k']: metrics[m]=round(metrics[m]/recommendation_count,4)
    return metrics

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--queries',type=Path,help='Optional frozen additional query set; use --split all')
    parser.add_argument('--predictions',type=Path,help='JSON array of {query_id, fixture_keys, degraded?}')
    parser.add_argument('--split',choices=['dev','heldout','all'],default='dev')
    parser.add_argument('--k',type=int,default=5)
    args=parser.parse_args()
    if args.k<1: parser.error('--k must be positive')
    products=json.loads((ROOT/'products.json').read_text()); queries=json.loads((ROOT/'queries.json').read_text())
    by_key=validate(products,queries)
    if args.queries:
        if args.split!='all': parser.error('Custom queries require --split all')
        queries=json.loads(args.queries.read_text(encoding='utf-8'))
        assert queries and len({q['query_id'] for q in queries})==len(queries)
        for q in queries:
            relevant=set(q['relevant_fixture_keys']);forbidden=set(q['forbidden_fixture_keys'])
            assert not relevant & forbidden and (relevant|forbidden)<=by_key.keys()
            assert all(eligible(by_key[k],q['constraints']) for k in relevant),q['query_id']
            assert bool(relevant)==(q['expected_behavior']=='recommend')
        print('Additional frozen query validation passed:',len(queries))
    print('Fixture validation passed: 100 products, 50 curated queries (30 dev / 20 heldout).')
    if args.predictions:
        chosen=[q for q in queries if args.split=='all' or q['split']==args.split]
        print(json.dumps(score(by_key,chosen,json.loads(args.predictions.read_text()),args.k),ensure_ascii=False,indent=2))

if __name__=='__main__': main()
