1. Backend should have an option to fire multiple requests at will. Not sure if it makes sense. if it can be fired individually , we wont need to microbatch for efficiency. BUt I can think of a case of a http service which can accept only 10 reqs per endpoint , but can be fired in parallel if ordering is not important though the batch size may be a 100. Even now , I dont think it makes any sense.

2. If there is a need for soring autoconfiguration I think it should be a seperate project which would bring this project as its depdendency. I dont want to pollute this project with spring.

3. SHould we introduce exponential back off retry  as well ? or should we thingk about reusing some other library to reuse.