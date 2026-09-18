#!/usr/bin/env python3
"""Offline behavior tests; these are not MySQL integration or semantic quality tests."""
import json
import unittest
from copy import deepcopy
from pathlib import Path
from evaluate import validate, score, eligible
from import_fixtures import digest, sql_for, literal
ROOT=Path(__file__).resolve().parent

class ToolsTest(unittest.TestCase):
    def setUp(self):
        self.products=json.loads((ROOT/'products.json').read_text())
        self.queries=json.loads((ROOT/'queries.json').read_text())
        self.by_key=validate(self.products,self.queries)
    def test_inventory_and_decimal_boundaries(self):
        constraints={'in_stock':True,'max_price':'80'}
        for n in [41,42,43,44,46]:
            self.assertFalse(eligible(self.by_key[f'rag-v1-{n:03}'],constraints))
        for n in [2,9,45]:
            self.assertTrue(eligible(self.by_key[f'rag-v1-{n:03}'],constraints))
        self.assertFalse(eligible(self.by_key['rag-v1-009'],{'max_price':'79.99'}))
    def test_score_rejects_incomplete_or_duplicate_evidence(self):
        q=self.queries[:2]
        with self.assertRaises(ValueError): score(self.by_key,q,[],5)
        with self.assertRaises(ValueError): score(self.by_key,q,[{'query_id':'q01','fixture_keys':[]},{'query_id':'q01','fixture_keys':[]}],5)
        with self.assertRaises(ValueError): score(self.by_key,q[:1],[{'query_id':'q01','fixture_keys':['rag-v1-001']*2}],5)
    def test_score_flags_invalid_business_results_beyond_top_k(self):
        q=self.queries[1:2]
        row={'query_id':'q02','fixture_keys':['rag-v1-002','rag-v1-044','unknown']}
        result=score(self.by_key,q,[row],1)
        self.assertEqual(result['labeled_recall_at_k'],1)
        self.assertEqual(result['hard_constraint_violating_queries'],1)
        self.assertEqual(result['forbidden_result_queries'],1)
        self.assertEqual(result['unknown_id_queries'],1)
    def test_no_match_and_degraded_are_separate(self):
        q=self.queries[-1:]
        result=score(self.by_key,q,[{'query_id':'q50','fixture_keys':[],'degraded':True}],5)
        self.assertEqual(result['no_match_correct'],1)
        self.assertEqual(result['degraded_queries'],1)
    def test_import_is_insert_only_and_content_versioned(self):
        sql=sql_for(self.products,None)
        self.assertNotIn('UPDATE ',sql)
        self.assertNotIn('DELETE ',sql)
        self.assertEqual(sql.count('INSERT INTO products '),100)
        self.assertEqual(sql.count('INSERT INTO stockpiles'),100)
        self.assertEqual(sql.count('START TRANSACTION'),1)
        changed=deepcopy(self.products[0]); changed['price']='10.00'
        self.assertNotEqual(digest(changed),digest(self.products[0]))
        self.assertNotIn("'; DROP",literal("'; DROP TABLE products; --"))

    def test_level_and_excluded_topics_are_scored_as_hard_constraints(self):
        self.assertFalse(eligible(self.by_key['rag-v1-003'], {'level':'入门'}))
        self.assertTrue(eligible(self.by_key['rag-v1-001'], {'level':'入门'}))
        self.assertFalse(eligible(self.by_key['rag-v1-003'], {'excludedTopics':['源码']}))
        self.assertTrue(eligible(self.by_key['rag-v1-022'], {'excludedTopics':['Java']}))

if __name__=='__main__': unittest.main()
