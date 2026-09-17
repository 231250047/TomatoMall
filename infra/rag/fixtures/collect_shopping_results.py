#!/usr/bin/env python3
"""Collect single-turn shopping answers from raw questions; never send labeled constraints.
Requires RAG_EVAL_TOKEN. Optional server log reads only safe diagnostic lines.
"""
import argparse,json,os,re,time,urllib.request
from pathlib import Path
from decimal import Decimal
ROOT=Path(__file__).resolve().parent

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--base-url',default='http://127.0.0.1:18080')
    ap.add_argument('--queries',type=Path,default=ROOT/'shopping-acceptance.json')
    ap.add_argument('--mapping',type=Path,required=True)
    ap.add_argument('--output',type=Path,required=True)
    ap.add_argument('--log-path',type=Path)
    args=ap.parse_args();token=os.environ['RAG_EVAL_TOKEN']
    mapping=json.loads(args.mapping.read_text());reverse={int(v):k for k,v in mapping.items()}
    queries=json.loads(args.queries.read_text());rows=[]
    for q in queries:
        offset=args.log_path.stat().st_size if args.log_path else 0;start=time.monotonic()
        req=urllib.request.Request(args.base_url.rstrip('/')+'/api/test/vectorstore/recommend',json.dumps({'query':q['query']}).encode(),{'Content-Type':'application/json','token':token})
        try:
            with urllib.request.urlopen(req,timeout=90) as r:response=json.load(r)
        except Exception as e:response={'error':type(e).__name__}
        lines=[]
        if args.log_path:
            with args.log_path.open('rb') as f:f.seek(offset);lines=f.read().decode(errors='replace').splitlines()
        diagnostics=[l.split(' : ',1)[-1] for l in lines if any(key in l for key in ['book_shopping route=','book_tool_selection tool=','book_tool_call outcome=','rag_selection outcome=','chat_call callId='])]
        answer=response.get('data','');ids=[int(x) for x in re.findall(r'商品编号 (\d+)',answer)];keys=[reverse.get(i,'unknown:'+str(i)) for i in ids]
        prices=[Decimal(x) for x in re.findall(r'￥(\d+(?:\.\d+)?)',answer)]
        routes=[m.group(1) for line in diagnostics if (m:=re.search(r'book_shopping route=(\w+)',line))]
        outcome={'target_hit':bool(set(keys)&set(q['targets'])) if q.get('targets') else None,
                 'over_budget':any(x>Decimal(q['maxPrice']) for x in prices) if q.get('maxPrice') else False,
                 'route_matches':routes[-1]==q['route'] if routes else None}
        if q.get('expected')=='no_match':outcome['expected_response']=not ids and '没有找到' in answer
        elif q.get('expected')=='clarify':outcome['expected_response']='预算' in answer and ('澄清' in answer or '确认' in answer)
        elif q.get('expected')=='greeting':outcome['expected_response']='你好' in answer and not ids
        elif q.get('expected')=='price_list':outcome['expected_response']=bool(ids) and '按价格升序' in answer
        rows.append({'id':q['id'],'query':q['query'],'response':response,'elapsed_seconds':round(time.monotonic()-start,2),'selected_fixture_keys':keys,'assessment':outcome,'diagnostics':diagnostics})
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
        print(q['id'],outcome,flush=True)
if __name__=='__main__':main()
