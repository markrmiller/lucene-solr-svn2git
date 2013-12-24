s = '{"searchState":{"lastDocID":1,"searcher":4},"totalHits":4,"facets":[{"2013":1,"2012":1,"2011":1,"2010":1}],"totalGroupedHits":4,"groups":[{"hits":[{"doc":2,"score":0.50458306,"fields":{"id":"0","body":"this is a <b>test<\/b>.  this sentence has <b>test<\/b> twice <b>test<\/b>.","price":"1.99","sortFields":{"price":1.9900000095367432},"date":"2012\/10\/17"}},{"doc":0,"score":0.3433253,"fields":{"id":"0","body":"this is a <b>test<\/b>.  here is a random sentence.  here is another sentence with <b>test<\/b> in it.","price":"5.99","sortFields":{"price":5.989999771118164},"date":"2010\/10\/17"}}],"groupValue":"Lisa","totalHits":2,"groupSortFields":{"price":1.9900000095367432}},{"hits":[{"doc":3,"score":0.7768564,"fields":{"id":"0","body":"this is a <b>test<\/b>.","price":"7.99","sortFields":{"price":7.989999771118164},"date":"2013\/10\/17"}}],"groupValue":"Bob","totalHits":1,"groupSortFields":{"price":7.989999771118164}},{"hits":[{"doc":1,"score":0.4806554,"fields":{"id":"0","body":"this is a <b>test<\/b>.  here is another sentence with <b>test<\/b> in it.","price":"11.99","sortFields":{"price":11.989999771118164},"date":"2011\/10\/17"}}],"groupValue":"Tom","totalHits":1,"groupSortFields":{"price":11.989999771118164}}]}'

import json
print(json.dumps(json.loads(s), sort_keys=True,
                  indent=4, separators=(',', ': ')))
