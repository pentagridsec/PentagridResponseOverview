# PentagridResponseOverview
Response Overview Extension for BurpSuite

Author: Tobias "floyd" Ospelt, @floyd_ch, http://www.floyd.ch

Pentagrid AG, https://www.pentagrid.ch

# Find exotic responses by grouping response bodies (response overview)
When the filter constraints are satisfied, the incoming response bodies (from all Burp tools!) is compared to all the groups we already created. A group is defined by it's very first member, which we keep in memory. If the response we are processing is 96.0% similar to that first member, it belongs to that group and only the "Group Size" counter is increased. That also means we won't store that response. If the response is not 96.0% similar to any group, it forms a new group and it is its first member.

# Trophy case
So far:
* I noticed a local file include vulnerability not picked up by the scanner.
* Found an SQL injection in a ClickHouse big data DBMS with a very exotic error message in the response.
* My attention was drawn to a lot of interesting functionality that I might have missed.
* Unfortunately nothing public so far, as I mainly do pentests with it. Did you find something? Let me know: floyd at floyd dot ch

# Howto use this extension
Usage is very simple:
* Add the website you test to the scope
* Test the web application (proxy, scanner, etc.) as you usually do.
* Check back on the Overview tab and have a look at all the responses. Did you notice all that functionality? Do you notice any strange error message? Any data in there that is new to you?

This extension analyses HTTP responses if:
* They are in scope
* They are not uninteresting mime types (Burp mime types JPEG, CSS, script, GIF, PNG, image)
* They do not have an uninteresting file extension (js, swf, css, zip, war, jar, doc, docx, xls, xlsx, pdf, exe, dll, png, jpeg, jpg, bmp, tif, tiff, gif, webp, svg, m3u, mp4, m4a, ogg, aac, flac, mp3, wav, avi, mov, mpeg, wmv, webm, woff, woff2, ttf)
* They are smaller than 1048576 bytes
* We aren't already displaying 1000 groups

# History
The first version of such a response overview that allows you to find anomalies I proposed in 2010 to the w3af project (see also a discussion here: https://github.com/andresriancho/w3af/issues/17345) and was written in Python. It never made it into mainline w3af, I don't remember why. I learned a lot about Python's difflib back then and optimized performance for the use case. I wrote a similar extension in Python for Burp in 2019 and again learned a lot about Python's difflib when I was working for modzero AG https://github.com/modzero/burp-ResponseClusterer. However, at one point I realized that the extension might be eating some of Burp's performance, which I at first ignored. In 2021 the extension broke completely as it wouldn't run with newer Jython versions anymore and while revisiting the code I realized that I coded a very memory-inefficient extension. So here we are, this is a new extension in 2021 in Kotlin with different features, again learning a lot from Python's difflib and different features. I did a performance improvement because I realized certain calculations are not necessary if we always compare against the same strings (as already implemented in the Python code).

# Performance discussion
In theory, the default settings could result in not-that-great performance of Burp. However, this is highly unlikely to occur. A test where all responses had maximum default response size (around 1MB) had to be compared against each other, the comparison took up to 30ms with precalculated bByteCount optimization (regular case) and up to 80ms without this optimization (happens only once for each entry). Multiplied by the maximum amount of groups (1000) this means in the absolute worst case this could mean up to 80 seconds of processing time. As there is a separate thread, this would be bearable, as the next comparison would then only take a maximum time of 30 seconds. But which web applications has many different responses of around 1MB? Let's hope not too many. Also don't forget that if the length of the responses differ more than 2%, the similarity matching takes no time at all due to the veryQuickRatio optimization.

# Ideas for future improvements

* We could hide the lines with the top 20% most seen groups (group size). Because that's probably just the regular HTML code or JSON responses that we see normally and are very uninteresting. But then we would kind of miss the "overview" property of the extension. So this is currently not done.
* Let me know if you think of any other improvements: tobias at pentagrid dot ch.
